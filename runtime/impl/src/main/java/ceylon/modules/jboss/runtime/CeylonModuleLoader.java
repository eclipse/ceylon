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

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.ceylon.cmr.api.ArtifactContext;
import org.eclipse.ceylon.cmr.api.MavenArtifactContext;
import org.eclipse.ceylon.cmr.api.ModuleDependencyInfo;
import org.eclipse.ceylon.cmr.api.RepositoryManager;
import org.eclipse.ceylon.common.Constants;
import org.eclipse.ceylon.common.ModuleUtil;
import org.eclipse.ceylon.common.Versions;
import org.eclipse.ceylon.model.cmr.ArtifactResult;
import org.eclipse.ceylon.model.cmr.ArtifactResultType;
import org.eclipse.ceylon.model.cmr.JDKUtils;
import org.eclipse.ceylon.model.cmr.ModuleScope;
import org.eclipse.ceylon.model.cmr.RuntimeResolver;
import org.eclipse.ceylon.model.cmr.JDKUtils.JDK;
import org.jboss.modules.AliasModuleSpec;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.LocalLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ModuleSpec.Builder;
import org.jboss.modules.NativeLibraryResourceLoader;
import org.jboss.modules.ResourceLoader;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.filter.PathFilter;
import org.jboss.modules.filter.PathFilters;

import ceylon.modules.api.runtime.LogChecker;
import ceylon.modules.api.util.ModuleVersion;
import ceylon.modules.jboss.repository.ResourceLoaderProvider;
import ceylon.modules.jboss.runtime.Graph.Vertex;

/**
 * Ceylon JBoss Module loader.
 * It understands Ceylon repository notion.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class CeylonModuleLoader extends ModuleLoader 
    implements RuntimeResolver {
    private static final ModuleIdentifier LANGUAGE;
    private static final ModuleIdentifier COMMON;
    private static final ModuleIdentifier MODEL;
    private static final ModuleIdentifier CMR;
    private static final ModuleIdentifier TYPECHECKER;
    private static final ModuleIdentifier COMPILER;
    private static final ModuleIdentifier LANGTOOLS_CLASSFILE;
    private static final ModuleIdentifier TOOL_PROVIDER;
    private static final ModuleIdentifier MODULES;
    private static final ModuleIdentifier LOGMANAGER;
    private static final ModuleIdentifier RUNTIME;
    private static final ModuleIdentifier ANTLR_RUNTIME;
    private static final ModuleIdentifier CLI;
    private static final ModuleIdentifier MARKDOWN_PAPERS;
    // Maven support
    private static final ModuleIdentifier AETHER;

    private static final String CEYLON_RUNTIME_PATH;
    private static final Set<ModuleIdentifier> BOOTSTRAP;
    private static final Set<ModuleIdentifier> BOOTSTRAP_OPTIONAL;

    private static final DependencySpec JDK_DEPENDENCY;
    private static final Set<String> JDK_MODULE_NAMES;

    private static final List<LogChecker> checkers;

    static {
        final String defaultVersion = System.getProperty(Constants.PROP_CEYLON_SYSTEM_VERSION, Versions.CEYLON_VERSION_NUMBER);
        LANGUAGE = ModuleIdentifier.create("ceylon.language", defaultVersion);
        COMMON = ModuleIdentifier.create("org.eclipse.ceylon.common", defaultVersion);
        MODEL = ModuleIdentifier.create("org.eclipse.ceylon.model", defaultVersion);
        CMR = ModuleIdentifier.create("org.eclipse.ceylon.module-resolver", defaultVersion);
        TYPECHECKER = ModuleIdentifier.create("org.eclipse.ceylon.typechecker", defaultVersion);
        COMPILER = ModuleIdentifier.create("org.eclipse.ceylon.compiler.java", defaultVersion);
        LANGTOOLS_CLASSFILE = ModuleIdentifier.create("org.eclipse.ceylon.langtools.classfile", defaultVersion);
        TOOL_PROVIDER = ModuleIdentifier.create("org.eclipse.ceylon.tool.provider", defaultVersion);
        CLI = ModuleIdentifier.create("org.eclipse.ceylon.cli", defaultVersion);

        // Maven support
        AETHER = ModuleIdentifier.create("org.eclipse.ceylon.aether", "3.3.9");
        
        MODULES = ModuleIdentifier.create("org.jboss.modules", Versions.DEPENDENCY_JBOSS_MODULES_VERSION);
        LOGMANAGER = ModuleIdentifier.create("org.jboss.logmanager", Versions.DEPENDENCY_LOGMANAGER_VERSION);
        RUNTIME = ModuleIdentifier.create("ceylon.runtime", defaultVersion);
        ANTLR_RUNTIME = ModuleIdentifier.create("org.antlr.runtime", "3.5.2");
        MARKDOWN_PAPERS = ModuleIdentifier.create("org.tautua.markdownpapers.core", "1.3.4");

        CEYLON_RUNTIME_PATH = ModuleVersion.class.getPackage().getName().replace(".", "/");

        BOOTSTRAP = new HashSet<>();
        BOOTSTRAP.add(LANGUAGE);
        BOOTSTRAP.add(COMMON);
        BOOTSTRAP.add(MODEL);
        BOOTSTRAP.add(CMR);
        BOOTSTRAP.add(TYPECHECKER);
        BOOTSTRAP.add(COMPILER);
        BOOTSTRAP.add(LANGTOOLS_CLASSFILE);
        BOOTSTRAP.add(TOOL_PROVIDER);
        BOOTSTRAP.add(CLI);
        BOOTSTRAP.add(MODULES);
        BOOTSTRAP.add(LOGMANAGER);
        BOOTSTRAP.add(RUNTIME);
        BOOTSTRAP.add(ANTLR_RUNTIME);
        
        BOOTSTRAP_OPTIONAL = new HashSet<>();
        BOOTSTRAP_OPTIONAL.add(MARKDOWN_PAPERS);
        // Maven support
        BOOTSTRAP_OPTIONAL.add(AETHER);
        

        Set<String> jdkPaths = new HashSet<>();
        JDK_MODULE_NAMES = new HashSet<>();
        // JDK
        for (String module : JDKUtils.getJDKModuleNames()) {
            Set<String> paths = JDKUtils.getJDKPathsByModule(module);
            jdkPaths.addAll(paths);
            JDK_MODULE_NAMES.add(module);
        }
        // Oracle
        for (String module : JDKUtils.getOracleJDKModuleNames()) {
            Set<String> paths = JDKUtils.getOracleJDKPathsByModule(module);
            JDK_MODULE_NAMES.add(module);
            jdkPaths.addAll(paths);
        }
        // always exported implicitely
        JDK_DEPENDENCY = DependencySpec.createSystemDependencySpec(jdkPaths, true);

        // add log checkers
        checkers = new ArrayList<>();
        for (LogChecker checker : ServiceLoader.load(LogChecker.class)) {
            checkers.add(checker);
        }
    }

    private RepositoryManager repository;
    private Map<ModuleIdentifier, List<DependencySpec>> dependencies = new ConcurrentHashMap<>();
    private Graph<ModuleIdentifier, ModuleIdentifier, Boolean> graph = new Graph<>();
    private boolean exportMavenImports = false;
    // Stef: enable back when we upgrade jboss modules
//    private Map<String,Object> classNamesToModules = new ConcurrentHashMap<>();
    

    public CeylonModuleLoader(RepositoryManager repository, boolean autoExportMavenDependencies) throws Exception {
        if (repository == null)
            throw new IllegalArgumentException("Null repository adapter");
        this.repository = repository;
        this.exportMavenImports = autoExportMavenDependencies;
        // initialise runtime modules
        init();
    }

    protected void init() throws Exception {
        // The runtime model needs knowledge of these modules existing at runtime, since the language module
        // implementation contains types from these modules
        ModuleLoader bootModuleLoader = org.jboss.modules.Module.getBootModuleLoader();
        for (ModuleIdentifier initialModule : BOOTSTRAP) {
            org.jboss.modules.Module module = bootModuleLoader.loadModule(initialModule);
            ArtifactResult moduleArtifactResult = findArtifact(initialModule);
            UtilRegistryTransformer.registerModule(initialModule.getName(), initialModule.getSlot(), moduleArtifactResult, SecurityActions.getClassLoader(module), false);
        }
        for (ModuleIdentifier initialModule : BOOTSTRAP_OPTIONAL) {
            try {
                org.jboss.modules.Module module = bootModuleLoader.loadModule(initialModule);
                ArtifactResult moduleArtifactResult = findArtifact(initialModule);
                UtilRegistryTransformer.registerModule(initialModule.getName(), initialModule.getSlot(), moduleArtifactResult, SecurityActions.getClassLoader(module), false);
            } catch (Exception ex) {
                // HACK: These are optional modules, just continue
            }
        }
    }

    /**
     * Update module.
     * Should be thread safe per module.
     *
     * @param module         the module to update
     * @param dependencySpec new dependency
     * @throws ModuleLoadException for any error
     */
    public void updateModule(org.jboss.modules.Module module, DependencySpec dependencySpec) throws ModuleLoadException {
        ModuleIdentifier mi = module.getIdentifier();
        List<DependencySpec> deps = dependencies.get(mi);
        if (deps == null) // should not really happen
            return;

        deps.add(dependencySpec);

        setAndRelinkDependencies(module, deps);
        refreshResourceLoaders(module);

        relink(mi, new HashSet<ModuleIdentifier>());
    }

    /**
     * Relink modules.
     *
     * @param mi      the current module identifier
     * @param visited already visited modules
     * @throws ModuleLoadException for any modules error
     */
    private void relink(ModuleIdentifier mi, Set<ModuleIdentifier> visited) throws ModuleLoadException {
        if (visited.add(mi) == false)
            return;

        Vertex<ModuleIdentifier, Boolean> v = graph.getVertex(mi);
        if (v == null)
            return;

        org.jboss.modules.Module module = preloadModule(mi);
        relink(module);

        Set<Graph.Edge<ModuleIdentifier, Boolean>> in = v.getIn();
        for (Graph.Edge<ModuleIdentifier, Boolean> edge : in) {
            if (edge.getCost()) {
                Graph.Vertex<ModuleIdentifier, Boolean> from = edge.getFrom();
                relink(from.getValue(), visited);
            }
        }
    }

    @Override
    protected org.jboss.modules.Module preloadModule(ModuleIdentifier mi) throws ModuleLoadException {
        mi = findOverride(mi);
        if (BOOTSTRAP.contains(mi) || BOOTSTRAP_OPTIONAL.contains(mi)) {
            return org.jboss.modules.Module.getBootModuleLoader().loadModule(mi);
        }
        return super.preloadModule(mi);
    }

    /**
     * Unload module.
     *
     * @param module the module
     */
    void unloadModule(org.jboss.modules.Module module) {
        dependencies.remove(module.getIdentifier());
        unloadModuleLocal(module);
    }

    protected ArtifactResult findArtifact(ModuleIdentifier mi) {
        String namespace = ModuleUtil.getNamespaceFromUri(mi.getName());
        String name = ModuleUtil.getModuleNameFromUri(mi.getName());
        final ArtifactContext context = new ArtifactContext(namespace, name, mi.getSlot(), ArtifactContext.CAR, ArtifactContext.JAR);
        return repository.getArtifactResult(context);
    }
    
    protected ModuleIdentifier findOverride(ModuleIdentifier mi) {
        String namespace = ModuleUtil.getNamespaceFromUri(mi.getName());
        String name = ModuleUtil.getModuleNameFromUri(mi.getName());
        final ArtifactContext context = new ArtifactContext(namespace, name, mi.getSlot(), ArtifactContext.CAR, ArtifactContext.JAR);
        ArtifactContext override = repository.getOverrides().applyOverrides(context);
        String newName = ModuleUtil.makeModuleName(override.getNamespace(), override.getName(), null);
        return ModuleIdentifier.create(newName, override.getVersion());
    }
    
    @Override
    public String resolveVersion(String moduleName, String moduleVersion) {
        if (org.eclipse.ceylon.model.typechecker.model.Module.DEFAULT_MODULE_NAME.equals(moduleName)) {
            // JBoss Modules turns default/null into default:main
            return null;
        }
        return findOverride(ModuleIdentifier.create(moduleName, moduleVersion)).getSlot();
    }

    protected boolean isLogging(List<DependencySpec> deps, Builder builder, ArtifactResult result) {
        for (LogChecker checker : checkers) {
            final List<ModuleDependencyInfo> replacements = checker.handle(result);
            if (replacements != null) {
                if (replacements.isEmpty()) {
                    throw new IllegalArgumentException(String.format("Log replacements cannot be empty - %s [%s]!", result, checker));
                }
                addLoggingModules(builder, deps, replacements);
                return true;
            }
        }
        return false;
    }

    @Override
    protected ModuleSpec findModule(ModuleIdentifier moduleIdentifier) throws ModuleLoadException {
        try {
            final ArtifactResult artifact = findArtifact(moduleIdentifier);
            if (artifact == null)
                return null;

            if (!artifact.version().equals(moduleIdentifier.getSlot())) {
                AliasModuleSpec alias = (AliasModuleSpec)ModuleSpec.buildAlias(moduleIdentifier, 
                        ModuleIdentifier.create(artifact.name(), artifact.version())).create();
                return alias;
            }
            
            final File moduleFile = artifact.artifact();
            final boolean isDefault = RepositoryManager.DEFAULT_MODULE.equals(moduleIdentifier.getName());
            boolean isMaven = artifact.type() == ArtifactResultType.MAVEN;

            final List<DependencySpec> deps = new ArrayList<>();

            ModuleSpec.Builder builder = ModuleSpec.build(moduleIdentifier);
            if(!ModuleUtil.isMavenJarlessModule(moduleFile)){
                // add module's jar
                ResourceLoader resourceLoader = ResourceLoaderProvider.getResourceLoader(moduleIdentifier, repository, moduleFile);
                // filter
                PathFilter filter = (artifact.filter() != null ? new CMRPathFilter(artifact.filter()) : PathFilters.acceptAll());
                // module resource root
                ResourceLoaderSpec rls = ResourceLoaderSpec.createResourceLoaderSpec(resourceLoader, filter);
                builder.addResourceRoot(rls);
            }
            // add potential native lib lookup
            ResourceLoader nativeLoader = new NativeLibraryResourceLoader(new File(moduleFile.getParent(), "lib"));
            builder.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(nativeLoader));

            Graph.Vertex<ModuleIdentifier, Boolean> vertex = graph.createVertex(moduleIdentifier, moduleIdentifier);

            DependencySpec lds = DependencySpec.createLocalDependencySpec();
            builder.addDependency(lds); // local resources
            deps.add(lds);

            if (isDefault == false) {
                Node<ArtifactResult> root = new Node<>();
                for (ArtifactResult i : artifact.dependencies()) {
                    // Skip test scopes
                    if(i.moduleScope() == ModuleScope.TEST)
                        continue;
                    String name = i.name();

                    // route logging to JBoss LogManager
                    if (isLogging(deps, builder, i)) {
                        continue;
                    }

                    if(JDKUtils.jdk.providesVersion(JDK.JDK9.version)){
                        // unalias jdk7-8 module names if we're running on jdk9+
                        name = JDKUtils.getJava9ModuleName(name, i.version());
                    }
                    // skip JDK modules
                    if (JDK_MODULE_NAMES.contains(name)) {
                        continue;
                    }

                    boolean isDepMaven = MavenArtifactContext.NAMESPACE.equals(i.namespace());

                    if (i.optional()) {
                        Node<ArtifactResult> current = root;
                        String[] tokens = name.split("\\.");
                        for (String token : tokens) {
                            Node<ArtifactResult> child = current.getChild(token);
                            if (child == null)
                                child = current.addChild(token);
                            current = child;
                        }
                        current.setValue(i);
                    } else {
                        DependencySpec mds = createModuleDependency(i, exportMavenImports && isMaven && isDepMaven);
                        builder.addDependency(mds);
                        deps.add(mds);
                    }

                    ModuleIdentifier mi = createModuleIdentifier(i);
                    Graph.Vertex<ModuleIdentifier, Boolean> dv = graph.createVertex(mi, mi);
                    Graph.Edge.create(i.exported() || (exportMavenImports && isMaven && isDepMaven), vertex, dv);
                }
                if (root.isEmpty() == false) {
                    LocalLoader onDemandLoader = new OnDemandLocalLoader(moduleIdentifier, this, root);
                    builder.setFallbackLoader(onDemandLoader);
                }
            }

            // automagically import the JDK module
            builder.addDependency(JDK_DEPENDENCY);
            // no need to track system deps -- cannot be updated anyway

            createModuleDependency(vertex, deps, builder, LANGUAGE, false);

            // add runtime utils
            final DependencySpec sds = DependencySpec.createModuleDependencySpec(
                PathFilters.match(CEYLON_RUNTIME_PATH),
                PathFilters.rejectAll(),
                this,
                RUNTIME,
                true
            );
            builder.addDependency(sds);
            deps.add(sds);
            Graph.Vertex<ModuleIdentifier, Boolean> sdsv = graph.createVertex(RUNTIME, RUNTIME);
            Graph.Edge.create(false, vertex, sdsv);

            dependencies.put(moduleIdentifier, deps);
            // Stef: enable back when we upgrade jboss modules
//            index(artifact.artifact(), moduleIdentifier);

            UtilRegistryTransformer transformer = new UtilRegistryTransformer(moduleIdentifier, artifact);
            builder.setClassFileTransformer(transformer);

            // make sure we set our own class loader factory so we can find the transformer back from the class loader
            // this is used in the language module to force module metamodel registration
            builder.setModuleClassLoaderFactory(new CeylonModuleClassLoader.CeylonModuleClassLoaderFactory(transformer));

            return builder.create();
        } catch (Exception e) {
            throw new ModuleLoadException(e);
        }
    }

    // Stef: enable back when we upgrade jboss modules
//    @SuppressWarnings("unchecked")
//    public List<ModuleIdentifier> findModuleForClass(String className){
//        Object value = classNamesToModules.get(className);
//        if(value == null)
//            return Collections.emptyList();
//        if(value instanceof ModuleIdentifier)
//            return Arrays.asList((ModuleIdentifier)value);
//        if(value instanceof List)
//            return (List<ModuleIdentifier>) value;
//        // WTF?
//        return Collections.emptyList();
//    }
//    
//    private void index(File artifact, ModuleIdentifier moduleIdentifier) {
//        if(artifact != null && artifact.exists() && artifact.canRead()){
//            // only index jars since we can't have CNFE with Ceylon modules
//            if(artifact.getName().toLowerCase().endsWith(".jar")){
//                System.err.println("Indexing "+artifact);
//                try (ZipFile zipFile = new ZipFile(artifact)) {
//                    Enumeration<? extends ZipEntry> entries = zipFile.entries();
//                    while(entries.hasMoreElements()){
//                        ZipEntry entry = entries.nextElement();
//                        if(entry.isDirectory())
//                            continue;
//                        String name = entry.getName();
//                        if(!name.toLowerCase().endsWith(".class"))
//                            continue;
//                        String className = name.replace('/', '.').substring(0, name.length()-6);
//                        Object value = classNamesToModules.get(className);
//                        if(value == null)
//                            classNamesToModules.put(className, moduleIdentifier);
//                        else if(value instanceof ModuleIdentifier){
//                            List<ModuleIdentifier> list = new ArrayList<ModuleIdentifier>(2);
//                            list.add((ModuleIdentifier) value);
//                            list.add(moduleIdentifier);
//                            classNamesToModules.put(className, list);
//                        }else if(value instanceof List){
//                            @SuppressWarnings("unchecked")
//                            List<ModuleIdentifier> list = (List<ModuleIdentifier>) value;
//                            list.add(moduleIdentifier);
//                        }
//                    }
//                } catch (IOException e) {
//                    // TODO Auto-generated catch block
//                    e.printStackTrace();
//                }
//            }
//        }
//    }

    private void addLoggingModules(Builder builder, List<DependencySpec> deps, List<ModuleDependencyInfo> replacements) {
        for (ModuleDependencyInfo mi : replacements) {
            ModuleIdentifier identifier = ModuleIdentifier.create(mi.getName(), mi.getVersion());
            final DependencySpec dependency = DependencySpec.createModuleDependencySpec(
                PathFilters.acceptAll(),
                mi.isExport() ? PathFilters.acceptAll() : PathFilters.rejectAll(),
                this,
                identifier,
                mi.isOptional()
            );
            builder.addDependency(dependency);
            deps.add(dependency);
        }
    }

    protected void createModuleDependency(Graph.Vertex<ModuleIdentifier, Boolean> vertex, List<DependencySpec> deps, ModuleSpec.Builder builder, ModuleIdentifier mi, boolean optional) {
        final DependencySpec dependency = DependencySpec.createModuleDependencySpec(
            PathFilters.acceptAll(),
            PathFilters.rejectAll(),
            this,
            mi,
            optional
        );
        builder.addDependency(dependency);
        deps.add(dependency);

        Graph.Vertex<ModuleIdentifier, Boolean> lv = graph.createVertex(mi, mi);
        Graph.Edge.create(false, vertex, lv);
    }

    /**
     * Create module dependency from import.
     *
     * @param i the import
     * @return new module dependency
     */
    DependencySpec createModuleDependency(ArtifactResult i, boolean forceExport) {
        // this should never happen
        if (JDK_MODULE_NAMES.contains(i.name()))
            return JDK_DEPENDENCY;

        final ModuleIdentifier mi = createModuleIdentifier(i);
        final boolean export = forceExport || i.exported();
        return DependencySpec.createModuleDependencySpec(
            PathFilters.getMetaInfSubdirectoriesWithoutMetaInfFilter(), // import everything?
            (export ? PathFilters.acceptAll() : PathFilters.rejectAll()),
            this,
            mi,
            i.optional()
        );
    }

    /**
     * Create module identifier.
     *
     * @param i the import
     * @return module identifer
     */
    static ModuleIdentifier createModuleIdentifier(ArtifactResult i) {
        String name = ModuleUtil.makeModuleName(i.namespace(), i.name(), null);
        return ModuleIdentifier.create(name, i.version());
    }

    @Override
    public String toString() {
        return "Ceylon ModuleLoader: " + repository;
    }

    public void loadModuleSynchronous(String name, String version) throws ModuleLoadException{
        ModuleIdentifier moduleIdentifier = ModuleIdentifier.create(name, version);
        Module module = loadModule(moduleIdentifier);
        ModuleClassLoader classLoader = module.getClassLoader();
        if(classLoader instanceof CeylonModuleClassLoader){
            ((CeylonModuleClassLoader) classLoader).registerInMetaModel();
            ((CeylonModuleClassLoader) classLoader).waitForRegisterThreads();
        }
    }
}
