package julien
package retrieval

import org.scalatest._
import org.scalamock.scalatest.proxy.MockFactory
import julien._

class UnorderedWindowSpec extends FlatSpec with MockFactory {

  def fixture = new {
    val mock1 = mock[PositionStatsView]
    val mock2 = mock[PositionStatsView]
    val mock3 = mock[PositionStatsView]
  }

  "An unordered window" should
  "require all children to be from the same index" in (pending)

  it should "complain if given a window size < 1 or window size is < # iterators" in {
    val f = fixture
    import f._

    val pos1 = Array(1,20)
    val pos2 = Array(21)
    val pos3 = Array(2, 19)

//    val p1 = Positions(pos1)
//    val p2 = Positions(pos2)
//    val p3 = Positions(pos3)
//
//    mock1.expects('positions)().returning(p1) noMoreThanTwice()
//    mock2.expects('positions)().returning(p2) noMoreThanTwice()
//    mock3.expects('positions)().returning(p3) noMoreThanTwice()

    val thrown = intercept[AssertionError] {
      val uw = UnorderedWindow(1, mock1, mock2, mock3)
    }
    //println(thrown.getMessage)
    assert(thrown.getMessage === "assumption failed: Window size must be >1 and at least as big as the number of iterators")

    val thrown1 = intercept[AssertionError] {
      val uw = UnorderedWindow(2, mock1, mock2, mock3)

    }
    assert(thrown1.getMessage === "assumption failed: Window size must be >1 and at least as big as the number of iterators")


  }

  it should "correctly count the number of windows" in {
    val f = fixture
    import f._

    val pos1 = Array(1,20)
    val pos2 = Array(21)
    val pos3 = Array(2, 19)

//    val p1 = Positions(pos1)
//    val p2 = Positions(pos2)
//    val p3 = Positions(pos3)
//
//    mock1.expects('positions)().returning(p1)
//    mock2.expects('positions)().returning(p2)
//    mock3.expects('positions)().returning(p3)

    val uw = UnorderedWindow(3, mock1, mock2, mock3)

//    val hits = Positions(Array(19))
//    expectResult(hits) {uw.positions}

    // val ow1 = OrderedWindow(1, mock1, mock2, mock3)

    //  expectResult(hits) {ow1.positions}
  }
}
