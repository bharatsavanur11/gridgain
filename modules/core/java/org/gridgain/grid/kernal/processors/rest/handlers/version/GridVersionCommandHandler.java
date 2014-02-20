// @java.file.header

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.rest.handlers.version;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.processors.rest.*;
import org.gridgain.grid.kernal.processors.rest.handlers.*;
import org.gridgain.grid.util.future.*;

import static org.gridgain.grid.kernal.processors.rest.GridRestCommand.*;

/**
 * Handler for {@link GridRestCommand#VERSION} command.
 *
 * @author @java.author
 * @version @java.version
 */
public class GridVersionCommandHandler extends GridRestCommandHandlerAdapter {
    /**
     * @param ctx Context.
     */
    public GridVersionCommandHandler(GridKernalContext ctx) {
        super(ctx);
    }

    /** {@inheritDoc} */
    @Override public boolean supported(GridRestCommand cmd) {
        return cmd == VERSION;
    }

    /** {@inheritDoc} */
    @Override public GridFuture<GridRestResponse> handleAsync(GridRestRequest req) {
        return new GridFinishedFuture<>(ctx, new GridRestResponse(ctx.version()));
    }
}