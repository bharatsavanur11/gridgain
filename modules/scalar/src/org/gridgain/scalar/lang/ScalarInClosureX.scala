// @scala.file.header

/*
 * ________               ______                    ______   _______
 * __  ___/_____________ ____  /______ _________    __/__ \  __  __ \
 * _____ \ _  ___/_  __ `/__  / _  __ `/__  ___/    ____/ /  _  / / /
 * ____/ / / /__  / /_/ / _  /  / /_/ / _  /        _  __/___/ /_/ /
 * /____/  \___/  \__,_/  /_/   \__,_/  /_/         /____/_(_)____/
 *
 */

package org.gridgain.scalar.lang

import org.gridgain.grid.lang._
import org.gridgain.grid._
import org.gridgain.grid.util.lang.GridInClosureX

/**
 * Peer deploy aware adapter for Java's `GridInClosureX`.
 *
 * @author @java.author
 * @version @java.version
 */
class ScalarInClosureX[T](private val f: T => Unit) extends GridInClosureX[T] {
    assert(f != null)

    peerDeployLike(f)

    /**
     * Delegates to passed in function.
     */
    @throws(classOf[GridException])
    def applyx(t: T) {
        f(t)
    }
}