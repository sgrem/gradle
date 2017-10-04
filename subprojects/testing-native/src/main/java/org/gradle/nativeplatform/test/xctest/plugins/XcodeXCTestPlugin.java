/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.nativeplatform.test.xctest.plugins;

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryVar;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.swift.tasks.CreateSwiftBundle;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.nativeplatform.tasks.LinkMachOBundle;
import org.gradle.nativeplatform.test.xctest.SwiftXCTestSuite;
import org.gradle.nativeplatform.test.xctest.SwiftXcodeXCTestSuite;
import org.gradle.nativeplatform.test.xctest.internal.MacOSSdkPlatformPathLocator;
import org.gradle.nativeplatform.test.xctest.tasks.XcTest;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * A plugin that sets up the infrastructure for testing native binaries with XCTest using Xcode runtime.
 *
 * @since 4.4
 */
@Incubating
public class XcodeXCTestPlugin implements Plugin<ProjectInternal> {
    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(XCTestBasePlugin.class);

        final TaskContainer tasks = project.getTasks();
        project.getComponents().withType(SwiftXcodeXCTestSuite.class, new Action<SwiftXcodeXCTestSuite>() {
            @Override
            public void execute(SwiftXcodeXCTestSuite testSuite) {
                configureCompileTask(tasks, testSuite, project.getServices());

                configureLinkTask(tasks, testSuite, project.getServices());

                Task lifecycleTask = createComponentLifecycleTestTask(tasks, testSuite);

                Task testTask = createTestTask(tasks, testSuite, project.getLayout());

                if (OperatingSystem.current().isMacOsX()) {
                    lifecycleTask.dependsOn(testTask);
                }

                configureTestTask(tasks, testSuite);
            }
        });
    }

    private static void configureCompileTask(TaskContainer tasks, SwiftXCTestSuite testSuite, final ServiceRegistry serviceRegistry) {
        Names names = Names.of(testSuite.getDevelopmentBinary().getName());
        tasks.withType(SwiftCompile.class).matching(withName(names.getCompileTaskName("swift"))).all(new Action<SwiftCompile>() {
            @Override
            public void execute(SwiftCompile compile) {
                ProviderFactory providers = serviceRegistry.get(ProviderFactory.class);
                final MacOSSdkPlatformPathLocator sdkPlatformPathLocator = serviceRegistry.get(MacOSSdkPlatformPathLocator.class);

                compile.getCompilerArgs().set(providers.provider(new Callable<List<String>>() {
                    @Override
                    public List<String> call() throws Exception {
                        File frameworkDir = new File(sdkPlatformPathLocator.find(), "Developer/Library/Frameworks");
                        return Lists.newArrayList("-g", "-F" + frameworkDir.getAbsolutePath());
                    }
                }));
            }
        });
    }

    private static void configureLinkTask(TaskContainer tasks, SwiftXCTestSuite testSuite, final ServiceRegistry serviceRegistry) {
        Names names = Names.of(testSuite.getDevelopmentBinary().getName());
        tasks.withType(LinkMachOBundle.class).matching(withName(names.getTaskName("link"))).all(new Action<LinkMachOBundle>() {
            @Override
            public void execute(LinkMachOBundle link) {
                ProviderFactory providers = serviceRegistry.get(ProviderFactory.class);
                final MacOSSdkPlatformPathLocator sdkPlatformPathLocator = serviceRegistry.get(MacOSSdkPlatformPathLocator.class);

                link.getLinkerArgs().set(providers.provider(new Callable<List<String>>() {
                    @Override
                    public List<String> call() throws Exception {
                        File frameworkDir = new File(sdkPlatformPathLocator.find(), "Developer/Library/Frameworks");
                        return Lists.newArrayList("-F" + frameworkDir.getAbsolutePath(), "-framework", "XCTest", "-Xlinker", "-rpath", "-Xlinker", "@executable_path/../Frameworks", "-Xlinker", "-rpath", "-Xlinker", "@loader_path/../Frameworks");
                    }
                }));
            }
        });
    }

    private static Task createComponentLifecycleTestTask(TaskContainer tasks, SwiftXCTestSuite testSuite) {
        final Task lifecycleTask = tasks.create(testSuite.getName());

        tasks.matching(withName(LifecycleBasePlugin.CHECK_TASK_NAME)).all(new Action<Task>() {
            @Override
            public void execute(Task checkTask) {
                checkTask.dependsOn(lifecycleTask);
            }
        });

        return lifecycleTask;
    }

    private static XcTest createTestTask(TaskContainer tasks, SwiftXCTestSuite testSuite, ProjectLayout layout) {
        DirectoryVar buildDirectory = layout.getBuildDirectory();
        final XcTest xcTest = tasks.create("xcTest", XcTest.class);

        // TODO - should respect changes to build directory
        xcTest.setBinResultsDir(buildDirectory.dir("results/" + testSuite.getName() + "/bin").get().getAsFile());
        // TODO - should respect changes to reports dir
        xcTest.getReports().getHtml().setDestination(buildDirectory.dir("reports/" + testSuite.getName()).map(new Transformer<File, Directory>() {
            @Override
            public File transform(Directory directory) {
                return directory.getAsFile();
            }
        }));
        xcTest.getReports().getJunitXml().setDestination(buildDirectory.dir("reports/" + testSuite.getName() + "/xml").map(new Transformer<File, Directory>() {
            @Override
            public File transform(Directory directory) {
                return directory.getAsFile();
            }
        }));
        xcTest.onlyIf(new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task element) {
                return xcTest.getTestBundleDir().exists();
            }
        });

        return xcTest;
    }

    private static void configureTestTask(final TaskContainer tasks, SwiftXCTestSuite testSuite) {
        Names names = Names.of(testSuite.getDevelopmentBinary().getName());
        tasks.withType(CreateSwiftBundle.class).matching(withName(names.getTaskName("bundleSwift"))).all(new Action<CreateSwiftBundle>() {
            @Override
            public void execute(CreateSwiftBundle bundle) {
                XcTest test = (XcTest) tasks.getByName("xcTest");

                test.setTestBundleDir(bundle.getOutputDir());
                test.setWorkingDir(bundle.getOutputDir().map(new Transformer<Directory, Directory>() {
                    @Override
                    public Directory transform(Directory directory) {
                        return directory.dir("../");
                    }
                }));
            }
        });
    }

    private static <T extends Task> Spec<? super T> withName(final String name) {
        return new Spec<T>() {
            @Override
            public boolean isSatisfiedBy(T element) {
                return element.getName().equals(name);
            }
        };
    }
}
