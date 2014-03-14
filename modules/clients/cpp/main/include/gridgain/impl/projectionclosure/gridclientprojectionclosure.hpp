/* 
 Copyright (C) GridGain Systems. All Rights Reserved.
 
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0
 
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

#ifndef GRID_CLIENT_PROJECTION_CLOSURE_HPP_INCLUDED
#define GRID_CLIENT_PROJECTION_CLOSURE_HPP_INCLUDED

#include "gridgain/gridclienttypedef.hpp"

class GridClientSocketAddress;
class GridClientCommandExecutor;

/**
 * Basic class for all closures.
 */
class ClientProjectionClosure {
public:
    /** Virtual destructor. */
    virtual ~ClientProjectionClosure() {}

    /** Apply executor to a certain connection parameters.
     *
     * @param node Node to apply this closure to.
     * @param connParams Host/port pair.
     * @param cmdExecutor Command executor.
     */
    virtual void apply(TGridClientNodePtr node, GridClientSocketAddress connParams, GridClientCommandExecutor& cmdExecutor) = 0;
};

#endif
