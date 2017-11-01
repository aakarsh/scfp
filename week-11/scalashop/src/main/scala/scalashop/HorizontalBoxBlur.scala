package scalashop

import org.scalameter._
import common._
import java.util.concurrent.ForkJoinTask

object HorizontalBoxBlurRunner {

  val standardConfig = config(
    Key.exec.minWarmupRuns -> 5,
    Key.exec.maxWarmupRuns -> 10,
    Key.exec.benchRuns -> 10,
    Key.verbose -> true) withWarmer(new Warmer.Default)

  def main(args: Array[String]): Unit = {
    val radius = 3
    val width = 1920
    val height = 1080

    val src = new Img(width, height)
    val dst = new Img(width, height)

    val seqtime = standardConfig measure {
      HorizontalBoxBlur.blur(src, dst, 0, height, radius)
    }

    println(s"sequential blur time: $seqtime ms")

    val numTasks = 32

    val partime = standardConfig measure {
      HorizontalBoxBlur.parBlur(src, dst, numTasks, radius)
    }

    println(s"fork/join blur time: $partime ms")
    println(s"speedup: ${seqtime / partime}")
  }

}

/**
 * A simple, trivially parallelizable computation.
 */
object HorizontalBoxBlur {

  /**
   * Blurs the rows of the source image `src` into the destination
   * image `dst`, starting with `from` and ending with `end`
   * (non-inclusive).
   *
   * Within each row, `blur` traverses the pixels by going from
   * left to right.
   */
  def blur(src: Img, dst: Img, from: Int, end: Int, radius: Int): Unit =
    for (x <- 0 until src.width ;          // left to right
         y <- from until end)   // left  to right
      dst(x, y) = boxBlurKernel(src, x, y, radius)

  /**
   * Blurs the rows of the source image in parallel using
   * `numTasks` tasks.
   *
   * Parallelization is done by stripping the source image `src` into
   * `numTasks` separate strips, where each strip is composed of some
   * number of rows.
   */
  def parBlur(src: Img, dst: Img, numTasks: Int, radius: Int): Unit =
  {    
    def toBlurTask(region: (Int,Int)): ForkJoinTask[Unit] =  
      region match { case (start, end) => task(blur(src, dst, start, end, radius)) }

    // Pair of strip borders of length numTasks, each of width stripSize 
    val stripBorders = src.horizontalStrips(numTasks)

    // Invoke task constructs in parallel and wait for them to finish.
    stripBorders.map(toBlurTask).map(_.join)
  }

}