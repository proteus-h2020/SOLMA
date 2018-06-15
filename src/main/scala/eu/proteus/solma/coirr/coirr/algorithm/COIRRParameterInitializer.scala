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

package eu.proteus.solma.coirr.algorithm

import breeze.linalg._
import eu.proteus.solma.coirr.COIRR


object COIRRParameterInitializer {

  def initConcrete(a: Double, b: Double, c: Double, lambda: Double, n: Int): Int => COIRR.COIRRModel =
    _ => (diag(DenseVector.fill(n){a}), DenseVector.fill(n){b}, DenseVector.fill(n){c}, lambda)
}
