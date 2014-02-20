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

import org.gridgain.grid.lang.GridBiPredicate

/**
 * Peer deploy aware adapter for Java's `GridPredicate2`.
 *
 * @author @java.author
 * @version @java.version
 */
class ScalarPredicate2[T1, T2](private val p: (T1, T2) => Boolean) extends GridBiPredicate[T1, T2] {
    assert(p != null)

    peerDeployLike(p)

    /**
     * Delegates to passed in function.
     */
    def apply(e1: T1, e2: T2) = p(e1, e2)
}