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

package org.gridgain.grid.kernal.processors.cache.query.continuous;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.managers.deployment.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.continuous.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.util.typedef.*;
import org.gridgain.grid.util.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * Continuous query handler.
 */
class GridCacheContinuousQueryHandler<K, V> implements GridContinuousHandler {
    /** Cache name. */
    private String cacheName;

    /** Topic for ordered messages. */
    private Object topic;

    /** Local callback. */
    @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
    private GridBiPredicate<UUID, Collection<Map.Entry<K, V>>> cb;

    /** Filter. */
    private GridBiPredicate<K, V> filter;

    /** Projection predicate */
    private GridPredicate<GridCacheEntry<K, V>> prjPred;

    /** Deployable object for filter. */
    private DeployableObject filterDep;

    /** Deployable object for Projection predicate. */
    private DeployableObject prjPredDep;

    /** Stopped flag. */
    private boolean stopped;

    /**
     * Required by {@link Externalizable}.
     */
    public GridCacheContinuousQueryHandler() {
        // No-op.
    }

    /**
     * @param cacheName Cache name.
     * @param topic Topic for ordered messages.
     * @param cb Local callback.
     * @param filter Filter.
     * @param prjPred Projection predicate.
     */
    GridCacheContinuousQueryHandler(@Nullable String cacheName, Object topic,
        GridBiPredicate<UUID, Collection<Map.Entry<K, V>>> cb, @Nullable GridBiPredicate<K, V> filter,
        @Nullable GridPredicate<GridCacheEntry<K, V>> prjPred) {
        assert topic != null;
        assert cb != null;

        this.cacheName = cacheName;
        this.topic = topic;
        this.cb = cb;
        this.filter = filter;
        this.prjPred = prjPred;
    }

    /** {@inheritDoc} */
    @Override public boolean register(final UUID nodeId, final UUID routineId, final GridKernalContext ctx)
        throws GridException {
        assert nodeId != null;
        assert routineId != null;
        assert ctx != null;

        if (cb != null)
            ctx.resource().injectGeneric(cb);

        if (filter != null)
            ctx.resource().injectGeneric(filter);

        final boolean loc = nodeId.equals(ctx.localNodeId());

        GridCacheContinuousQueryListener<K, V> lsnr = new GridCacheContinuousQueryListener<K, V>() {
            @Override public void onEntryUpdate(GridCacheContinuousQueryEntry<K, V> e) {
                boolean notify;

                GridCacheFlag[] f = cacheContext(ctx).forceLocalRead();

                try {
                    notify = (prjPred == null || prjPred.apply(e)) &&
                        (filter == null || filter.apply(e.getKey(), e.getValue()));
                }
                finally {
                    cacheContext(ctx).forceFlags(f);
                }

                if (notify) {
                    if (loc) {
                        boolean stop = false;

                        synchronized (GridCacheContinuousQueryHandler.this) {
                            if (!stopped)
                                stop = stopped = !cb.apply(nodeId, F.<Map.Entry<K, V>>asList(e));
                        }

                        if (stop)
                            ctx.continuous().stopRoutine(routineId);
                    }
                    else {
                        try {
                            if (ctx.config().isPeerClassLoadingEnabled() &&
                                U.hasCache(ctx.discovery().node(nodeId), cacheName)) {
                                e.p2pMarshal(ctx.config().getMarshaller());

                                e.cacheName(cacheName);

                                GridCacheDeploymentManager depMgr =
                                    ctx.cache().internalCache(cacheName).context().deploy();

                                depMgr.prepare(e);
                            }

                            ctx.continuous().addNotification(nodeId, routineId, e, topic);
                        }
                        catch (GridException ex) {
                            U.error(ctx.log(getClass()), "Failed to send event notification to node: " + nodeId, ex);
                        }
                    }
                }
            }
        };

        return cacheContext(ctx).continuousQueries().registerListener(routineId, lsnr);
    }

    /** {@inheritDoc} */
    @Override public void onListenerRegistered(UUID routineId, GridKernalContext ctx) {
        cacheContext(ctx).continuousQueries().iterate(routineId);
    }

    /** {@inheritDoc} */
    @Override public void unregister(UUID routineId, GridKernalContext ctx) {
        assert routineId != null;
        assert ctx != null;

        cacheContext(ctx).continuousQueries().unregisterListener(routineId);
    }

    /** {@inheritDoc} */
    @Override public void notifyCallback(UUID nodeId, UUID routineId, Collection<?> objs, GridKernalContext ctx) {
        assert nodeId != null;
        assert routineId != null;
        assert objs != null;
        assert ctx != null;

        Collection<Map.Entry<K, V>> entries = (Collection<Map.Entry<K, V>>)objs;

        if (ctx.config().isPeerClassLoadingEnabled()) {
            for (Map.Entry<K, V> e : entries) {
                assert e instanceof GridCacheContinuousQueryEntry;

                GridCacheContinuousQueryEntry<K, V> qe = (GridCacheContinuousQueryEntry<K, V>)e;

                GridCacheAdapter cache = ctx.cache().internalCache(qe.cacheName());

                ClassLoader ldr = null;

                if (cache != null) {
                    GridCacheDeploymentManager depMgr = cache.context().deploy();

                    GridDeploymentInfo depInfo = qe.deployInfo();

                    if (depInfo != null) {
                        depMgr.p2pContext(nodeId, depInfo.classLoaderId(), depInfo.userVersion(), depInfo.deployMode(),
                            depInfo.participants(), depInfo.localDeploymentOwner());
                    }

                    ldr = depMgr.globalLoader();
                }
                else {
                    U.warn(ctx.log(getClass()), "Received cache event for cache that is not configured locally " +
                        "when peer class loading is enabled: " + qe.cacheName() + ". Will try to unmarshal " +
                        "with default class loader.");
                }

                try {
                    qe.p2pUnmarshal(ctx.config().getMarshaller(), ldr);
                }
                catch (GridException ex) {
                    U.error(ctx.log(getClass()), "Failed to unmarshal entry.", ex);
                }
            }
        }

        boolean stop = false;

        synchronized (this) {
            if (!stopped)
                stop = stopped = !cb.apply(nodeId, entries);
        }

        if (stop)
            ctx.continuous().stopRoutine(routineId);
    }

    /** {@inheritDoc} */
    @Override public void p2pMarshal(GridKernalContext ctx) throws GridException {
        assert ctx != null;
        assert ctx.config().isPeerClassLoadingEnabled();

        if (filter != null)
            filterDep = new DeployableObject(filter, ctx);

        if (prjPred != null)
            prjPredDep = new DeployableObject(prjPred, ctx);
    }

    /** {@inheritDoc} */
    @Override public void p2pUnmarshal(UUID nodeId, GridKernalContext ctx) throws GridException {
        assert nodeId != null;
        assert ctx != null;
        assert ctx.config().isPeerClassLoadingEnabled();

        if (filterDep != null)
            filter = filterDep.unmarshal(nodeId, ctx);

        if (prjPredDep != null)
            prjPred = prjPredDep.unmarshal(nodeId, ctx);
    }

    /** {@inheritDoc} */
    @Nullable @Override public Object orderedTopic() {
        return topic;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeString(out, cacheName);
        out.writeObject(topic);

        boolean b = filterDep != null;

        out.writeBoolean(b);

        if (b)
            out.writeObject(filterDep);
        else
            out.writeObject(filter);

        b = prjPredDep != null;

        out.writeBoolean(b);

        if (b)
            out.writeObject(prjPredDep);
        else
            out.writeObject(prjPred);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        cacheName = U.readString(in);
        topic = in.readObject();

        boolean b = in.readBoolean();

        if (b)
            filterDep = (DeployableObject)in.readObject();
        else
            filter = (GridBiPredicate<K, V>)in.readObject();

        b = in.readBoolean();

        if (b)
            prjPredDep = (DeployableObject)in.readObject();
        else
            prjPred = (GridPredicate<GridCacheEntry<K, V>>)in.readObject();
    }

    /**
     * @param ctx Kernal context.
     * @return Cache context.
     */
    private GridCacheContext<K, V> cacheContext(GridKernalContext ctx) {
        assert ctx != null;

        return ctx.cache().<K, V>internalCache(cacheName).context();
    }

    /**
     * Deployable object.
     */
    private static class DeployableObject implements Externalizable {
        /** Serialized object. */
        private byte[] bytes;

        /** Deployment class name. */
        private String clsName;

        /** Deployment info. */
        private GridDeploymentInfo depInfo;

        /**
         * Required by {@link Externalizable}.
         */
        public DeployableObject() {
            // No-op.
        }

        /**
         * @param obj Object.
         * @param ctx Kernal context.
         * @throws GridException In case of error.
         */
        private DeployableObject(Object obj, GridKernalContext ctx) throws GridException {
            assert obj != null;
            assert ctx != null;

            Class cls = U.detectClass(obj);

            clsName = cls.getName();

            GridDeployment dep = ctx.deploy().deploy(cls, U.detectClassLoader(cls));

            if (dep == null)
                throw new GridDeploymentException("Failed to deploy object: " + obj);

            depInfo = new GridDeploymentInfoBean(dep);

            bytes = ctx.config().getMarshaller().marshal(obj);
        }

        /**
         * @param nodeId Node ID.
         * @param ctx Kernal context.
         * @return Deserialized object.
         * @throws GridException In case of error.
         */
        <T> T unmarshal(UUID nodeId, GridKernalContext ctx) throws GridException {
            assert ctx != null;

            GridDeployment dep = ctx.deploy().getGlobalDeployment(depInfo.deployMode(), clsName, clsName,
                depInfo.userVersion(), nodeId, depInfo.classLoaderId(), depInfo.participants(), null);

            if (dep == null)
                throw new GridDeploymentException("Failed to obtain deployment for class: " + clsName);

            return ctx.config().getMarshaller().unmarshal(bytes, dep.classLoader());
        }

        /** {@inheritDoc} */
        @Override public void writeExternal(ObjectOutput out) throws IOException {
            U.writeByteArray(out, bytes);
            U.writeString(out, clsName);
            out.writeObject(depInfo);
        }

        /** {@inheritDoc} */
        @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            bytes = U.readByteArray(in);
            clsName = U.readString(in);
            depInfo = (GridDeploymentInfo)in.readObject();
        }
    }
}
