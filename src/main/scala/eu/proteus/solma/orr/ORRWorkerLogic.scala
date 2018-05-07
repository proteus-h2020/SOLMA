/*
 * Copyright (C) 2017 The Proteus Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.proteus.solma.orr

import breeze.linalg.{DenseMatrix, DenseVector}
import eu.proteus.annotations.Proteus
import eu.proteus.solma.orr.ORR.{ORRModel, ORRStreamEvent, UnlabeledVector}
import eu.proteus.solma.orr.algorithm.BaseORRAlgorithm
import hu.sztaki.ilab.ps.{ParameterServerClient, WorkerLogic}
import org.apache.flink.ml.math.Breeze._

import scala.collection.mutable

@Proteus
class ORRWorkerLogic(
                    algorithm: BaseORRAlgorithm[UnlabeledVector, Double, ORRModel]
                    ) extends WorkerLogic[ORRStreamEvent, ORR.ORRModel, (ORR.UnlabeledVector, Double)] {

  val unpredictedVecs = new mutable.Queue[ORR.UnlabeledVector]()
  val unlabeledVecs = new mutable.HashMap[Long, ORR.UnlabeledVector]()
  val labeledVecs = new mutable.Queue[(ORR.UnlabeledVector, Double, Long)]()
  val maxTTL: Long = 60 * 60 * 1000

  override def onRecv(
                      data: ORRStreamEvent,
                      ps: ParameterServerClient[ORR.ORRModel, (ORR.UnlabeledVector, Double)]): Unit = {

    data match {
      case Left(v) =>
        // store unlabelled point and pull
        unpredictedVecs.enqueue(v._2.data.asBreeze)
        unlabeledVecs(v._1) = v._2.data.asBreeze
      case Right(v) =>
        // we got a labelled point
        unlabeledVecs.remove(v._1) match {
          case Some(unlabeledVector) => labeledVecs.enqueue((unlabeledVector, v._2, v._1))
          case None =>
        }
    }
    ps.pull(0)
  }

  override def onPullRecv(
                          paramId: Int,
                          currentModel: ORR.ORRModel,
                          ps: ParameterServerClient[ORR.ORRModel, (ORR.UnlabeledVector, Double)]): Unit = {

    var modelOpt: Option[ORR.ORRModel] = None

    while (unpredictedVecs.nonEmpty) {
      val dataPoint = unpredictedVecs.dequeue()
      ps.output((dataPoint, algorithm.predict(dataPoint, currentModel)))
    }

    while (labeledVecs.nonEmpty) {
      val restedData = labeledVecs.dequeue()
      ps.push(0, algorithm.delta(restedData._1, currentModel, restedData._2))
    }

  }
}
