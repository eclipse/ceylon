/********************************************************************************
 * Copyright (c) 2011-2017 Red Hat Inc. and/or its affiliates and others
 *
 * This program and the accompanying materials are made available under the 
 * terms of the Apache License, Version 2.0 which is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: Apache-2.0 
 ********************************************************************************/
package ceylon.modules.jboss.runtime;

import org.eclipse.ceylon.cmr.api.RepositoryManager;
import org.eclipse.ceylon.cmr.api.RepositoryManagerBuilder;
import org.eclipse.ceylon.cmr.ceylon.CeylonUtils;
import org.eclipse.ceylon.cmr.impl.CMRJULLogger;
import org.eclipse.ceylon.cmr.spi.ContentTransformer;
import org.eclipse.ceylon.cmr.spi.MergeStrategy;
import org.eclipse.ceylon.common.log.Logger;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleNotFoundException;

import ceylon.modules.CeylonRuntimeException;
import ceylon.modules.Configuration;
import ceylon.modules.Main;
import ceylon.modules.api.runtime.AbstractRuntime;
import ceylon.modules.spi.runtime.ClassLoaderHolder;

/**
 * Abstract Ceylon JBoss Modules runtime.
 * Useful for potential extension.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class AbstractJBossRuntime extends AbstractRuntime {
    public ClassLoaderHolder createClassLoader(String name, String version, Configuration conf) throws Exception {
        if (RepositoryManager.DEFAULT_MODULE.equals(name)) {
            if (version != null) {
                throw new CeylonRuntimeException("Invalid module identifier: default module should not have any version");
            }
        } else {
            if (version == null) {
                StringBuilder sb = new StringBuilder("Invalid module identifier: missing required version");
                sb.append(" (should be of the form ");
                sb.append(name);
                sb.append("/version)");
                throw new CeylonRuntimeException(sb.toString());
            }
        }

        ModuleIdentifier moduleIdentifier;
        try {
            moduleIdentifier = ModuleIdentifier.fromString(name + ":" + version);
        } catch (IllegalArgumentException x) {
            CeylonRuntimeException cre = new CeylonRuntimeException("Invalid module name or version: contains invalid characters");
            cre.initCause(x);
            throw cre;
        }
        try {
            ModuleLoader moduleLoader = createModuleLoader(conf);
            Module module = moduleLoader.loadModule(moduleIdentifier);
            return new ClassLoaderHolderImpl(module);
        } catch (ModuleNotFoundException e) {
            String spec = e.getMessage();
            int p = spec.lastIndexOf(':');
            if (p >= 0) {
                spec = spec.substring(0, p) + "/" + spec.substring(p + 1);
            }
            spec = spec.replace("\\:", ":"); //ModuleIdentifier escapes :
            String msg = "Could not find module: " + spec + " (invalid version?";
            if (name.equals("ceylon.language")) {
                msg += " try running with '--link-with-current-distribution'";
            }
            msg += ")";
            final CeylonRuntimeException cre = new CeylonRuntimeException(msg);
            cre.initCause(e);
//            e.printStackTrace();
            throw cre;
        }
    }

    private RepositoryManager createRepository(Configuration conf, boolean offline) {
        Logger log = new CMRJULLogger();
        final RepositoryManagerBuilder builder = CeylonUtils.repoManager()
            .cwd(conf.cwd)
            .systemRepo(conf.systemRepository)
            .cacheRepo(conf.cacheRepository)
            .overrides(conf.overrides)
            .upgradeDist(conf.upgradeDist)
            .noDefaultRepos(conf.noDefaultRepositories)
            .noOutRepo(true)
            .userRepos(conf.repositories)
            .offline(offline || conf.offline)
            .logger(log)
            .buildManagerBuilder();

        final MergeStrategy ms = getService(MergeStrategy.class, conf);
        if (ms != null)
            builder.mergeStrategy(ms);

        if (conf.cacheContent)
            builder.cacheContent();

        final ContentTransformer ct = getService(ContentTransformer.class, conf);
        if (ct != null)
            builder.contentTransformer(ct);

        return builder.buildRepository();
    }


    /**
     * Get repository extension.
     *
     * @param conf the configuration
     * @return repository extension
     */
    protected RepositoryManager createRepository(Configuration conf) {
        return createRepository(conf, false);
    }

    /**
     * Get repository service.
     *
     * @param serviceType the service type
     * @param conf        the configuration
     * @return service instance or null
     */
    protected <T> T getService(Class<T> serviceType, Configuration conf) {
        try {
            String impl = conf.impl.get(serviceType.getName());
            return (impl != null) ? Main.createInstance(serviceType, impl) : null;
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot instantiate service: " + serviceType.getName(), e);
        }
    }

    /**
     * Create module loader.
     *
     * @param conf the configuration
     * @return the module loader
     * @throws Exception for any error during creation
     */
    protected abstract ModuleLoader createModuleLoader(Configuration conf) throws Exception;
}
