/*
 * Copyright 2014–2017 SlamData Inc.
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

package quasar.qscript.qsu

import quasar.{Data, Qspec}
import quasar.qscript.{Hole, LeftSide, MapFuncsCore, MFC, RightSide, SrcHole}
import slamdata.Predef._

import matryoshka.data.Fix
import scalaz.{Need, StateT}

object RewriteGroupByArraysSpec extends Qspec with QSUTTypes[Fix] {
  import QSUGraph.Extractors._

  type F[A] = StateT[Need, Long, A]

  val qsu = QScriptUniform.DslT[Fix]
  val rw = RewriteGroupByArrays[Fix]

  "re-nesting flat groupby structure" should {
    "leave a single groupby alone" in {
      val qgraph = QSUGraph.fromTree[Fix](
        qsu.groupBy(
          qsu.unreferenced(),
          qsu.autojoin2((
            qsu.unreferenced(),
            qsu.cstr("foo"),
            _(MapFuncsCore.ProjectKey(_, _))))))

      eval(rw[F](qgraph)) must beLike {
        case GroupBy(
          Unreferenced(),
          AutoJoin2C(
            Unreferenced(),
            DataConstantMapped(Data.Str("foo")),
            MapFuncsCore.ProjectKey(LeftSide, RightSide))) => ok
      }
    }

    "rewrite a pair of group keys" in {
      val qgraph = QSUGraph.fromTree[Fix](
        qsu.groupBy(
          qsu.unreferenced(),
          qsu.autojoin2((
            qsu.unary(
              qsu.autojoin2((
                qsu.unreferenced(),
                qsu.cstr("foo"),
                _(MapFuncsCore.ProjectKey(_, _)))),
              MFC(MapFuncsCore.MakeArray[Fix, Hole](SrcHole))),
            qsu.unary(
              qsu.autojoin2((
                qsu.unreferenced(),
                qsu.cstr("bar"),
                _(MapFuncsCore.ProjectKey(_, _)))),
              MFC(MapFuncsCore.MakeArray[Fix, Hole](SrcHole))),
            _(MapFuncsCore.ConcatArrays(_, _))))))

      eval(rw[F](qgraph)) must beLike {
        case GroupBy(
          GroupBy(
            Unreferenced(),
            AutoJoin2C(
              Unreferenced(),
              DataConstantMapped(Data.Str("foo")),
              MapFuncsCore.ProjectKey(LeftSide, RightSide))),
          AutoJoin2C(
            Unreferenced(),
            DataConstantMapped(Data.Str("bar")),
            MapFuncsCore.ProjectKey(LeftSide, RightSide))) => ok
      }
    }

    "rewrite a triple of group keys" in {
      val qgraph = QSUGraph.fromTree[Fix](
        qsu.groupBy(
          qsu.unreferenced(),
          qsu.autojoin2((
            qsu.autojoin2((
              qsu.unary(
                qsu.autojoin2((
                  qsu.unreferenced(),
                  qsu.cstr("foo"),
                  _(MapFuncsCore.ProjectKey(_, _)))),
                MFC(MapFuncsCore.MakeArray[Fix, Hole](SrcHole))),
              qsu.unary(
                qsu.autojoin2((
                  qsu.unreferenced(),
                  qsu.cstr("bar"),
                  _(MapFuncsCore.ProjectKey(_, _)))),
                MFC(MapFuncsCore.MakeArray[Fix, Hole](SrcHole))),
              _(MapFuncsCore.ConcatArrays(_, _)))),
            qsu.unary(
              qsu.autojoin2((
                qsu.unreferenced(),
                qsu.cstr("baz"),
                _(MapFuncsCore.ProjectKey(_, _)))),
              MFC(MapFuncsCore.MakeArray[Fix, Hole](SrcHole))),
            _(MapFuncsCore.ConcatArrays(_, _))))))

      eval(rw[F](qgraph)) must beLike {
        case GroupBy(
          GroupBy(
            GroupBy(
              Unreferenced(),
              AutoJoin2C(
                Unreferenced(),
                DataConstantMapped(Data.Str("foo")),
                MapFuncsCore.ProjectKey(LeftSide, RightSide))),
            AutoJoin2C(
              Unreferenced(),
              DataConstantMapped(Data.Str("bar")),
              MapFuncsCore.ProjectKey(LeftSide, RightSide))),
          AutoJoin2C(
            Unreferenced(),
            DataConstantMapped(Data.Str("baz")),
            MapFuncsCore.ProjectKey(LeftSide, RightSide))) => ok
      }
    }
  }

  def eval[A](fa: F[A]): A = fa.eval(0L).value
}
