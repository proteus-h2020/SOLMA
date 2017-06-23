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

package eu.proteus.solma.sax

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Map.Entry
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.{HashMap => JHashMap}
import java.util.{HashSet => JHashSet}
import java.util.{Map => JMap}

object BagDictionary{

  /**
   * Load the bag dictionary from file.
   * @param basePath The base path that contains dumped classes.
   * @return A [[BagDictionary]].
   */
  def fromModel(basePath: String) : BagDictionary = {
    val bags : JMap[String, WordBag] = new JHashMap[String, WordBag]()
    val base = new File(basePath)
    if(base.exists() && base.isDirectory){
      val storedBags = base.listFiles().filter(_.isFile)
      storedBags.foreach(f => {
        val wordBag = WordBag.fromFile(f.getAbsolutePath)
        bags.put(wordBag.id.get, wordBag)
      })
    }else{
      throw new UnsupportedOperationException("Expecting base directory with the model")
    }
    new BagDictionary(bags)
  }

}

/**
 * Dictionary to store all the words.
 */
case class BagDictionary(bags : JMap[String, WordBag] = new JHashMap[String, WordBag]()){

  /**
   * Maximum number of words in any given class.
   * @return The maximum number of words.
   */
  def maxWordsOnBag() : Int = {
    var max = 0
    val iterator = this.bags.entrySet().iterator()
    while(iterator.hasNext){
      val bag = iterator.next()
      if(bag.getValue.words.size() > max){
        max = bag.getValue.words.size()
      }
    }
    max
  }

  /**
   * Number of words in the corpus.
   * @return The number of words.
   */
  def maxWords() : Int = {
    val aux = new JHashSet[String]()
    this.bags.values().forEach(new Consumer[WordBag] {
      override def accept(t: WordBag): Unit = {
        aux.addAll(t.words.keySet())
      }
    })
    aux.size()
  }

  /**
   * Retrieve the number of classes that contain a given word.
   * @param word The word.
   * @return The number of classes with the word.
   */
  private def bagsWithWord(word: String) : Long = {
    var number = 0
    this.bags.values().forEach(new Consumer[WordBag] {
      override def accept(t: WordBag): Unit = {
        if(t.words.containsKey(word)){
          number = number + 1
        }
      }
    })
    number
  }

  /**
   * This method builds a matrix-like structure where instead of containing the term
   * frequency, the TF * IDF value is used. To compute this value, we use the following
   * approach:
   *
   * {{{
   *   tfIdf(word_k, class_i) = log( 1 + freq(word_k, class_i)) x
   *     1 + log ( num_classes / bagsWithWord(word_k))
   * }}}
   *
   * Notice that the IDF part of the equation is incremented by one as to allow the dictionary
   * to work under single-class scenarios.
   *
   */
  def buildTFIDFMatrix() : Unit = {
    val numClasses = this.bags.size()
    val dictionary = this
    this.bags.entrySet().forEach(new Consumer[Entry[String, WordBag]] {
      override def accept(e: Entry[String, WordBag]): Unit = {
        val tfIdf = new JHashMap[String, Double]()
        e.getValue.words.forEach(new BiConsumer[String, Long] {
          override def accept(word: String, freq: Long): Unit = {
            val wordTfIdf = Math.log(1 + freq) *
              (1 + Math.log(numClasses / dictionary.bagsWithWord(word)))
            tfIdf.put(word, wordTfIdf)
          }
        })
        e.getValue.tfIdf.putAll(tfIdf)
      }
    })

  }


  /**
   * Predict the class of a given vector.
   * @param key The partition key associated with the prediction.
   * @param vector The vector to be compared.
   * @return A [[SAXPrediction]].
   */
  def predict(key: Int, vector: JMap[String, Long]) : SAXPrediction = {

    if(this.bags.isEmpty){
      throw new UnsupportedOperationException("No classes available for prediction")
    }

    var result : Option[SAXPrediction] = None
    this.bags.values().forEach(new Consumer[WordBag] {
      override def accept(wb: WordBag) : Unit = {
        val similarity = wb.similarity(vector)
        if(result.isEmpty || result.get.similarity < similarity){
          result = Some(new SAXPrediction(key, wb.id.get, similarity))
        }
      }
    })

    result.get
  }

  /**
   * Store the dictionary in a given directory. A file per class will be created in that
   * directory.
   * @param basePath The base path.
   */
  def storeDictionary(basePath: String) : Unit = {
    Files.createDirectories(Paths.get(basePath))
    this.bags.forEach(new BiConsumer[String, WordBag] {
      override def accept(id: String, wb: WordBag): Unit = {
        wb.storeWordBag(basePath)
      }
    })
  }

}
