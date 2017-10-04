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

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.nativeplatform.tasks.AbstractLinkTask;
import org.gradle.nativeplatform.test.xctest.SwiftXCTestSuite;

/**
 * A plugin that sets up the infrastructure for testing native binaries with XCTest.
 *
 * @since 4.4
 */
@Incubating
public class XCTestBasePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        final TaskContainer tasks = project.getTasks();

        project.getComponents().withType(SwiftXCTestSuite.class).all(new Action<SwiftXCTestSuite>() {
            @Override
            public void execute(SwiftXCTestSuite testSuite) {
                configureTestedComponentIfAvailable(tasks, testSuite);
            }
        });
    }

    private static void configureTestedComponentIfAvailable(final TaskContainer tasks, final SwiftXCTestSuite testSuite) {
        if (testSuite.getTestedComponent() == null) {
            return;
        }

        Names mainNames = Names.of(testSuite.getTestedComponent().getDevelopmentBinary().getName());
        tasks.withType(SwiftCompile.class).matching(withName(mainNames.getCompileTaskName("swift"))).all(new Action<SwiftCompile>() {
           @Override
           public void execute(final SwiftCompile compileMain) {
                Names testNames = Names.of(testSuite.getDevelopmentBinary().getName());

                tasks.withType(SwiftCompile.class).matching(withName(testNames.getCompileTaskName("swift"))).all(new Action<SwiftCompile>() {
                    @Override
                    public void execute(SwiftCompile task) {
                        task.includes(compileMain.getObjectFileDir());
                    }
                });

                tasks.withType(AbstractLinkTask.class).matching(withName(testNames.getTaskName("link"))).all(new Action<AbstractLinkTask>() {
                    @Override
                    public void execute(AbstractLinkTask task) {
                        task.source(compileMain.getObjectFileDir().getAsFileTree().matching(new PatternSet().include("**/*.obj", "**/*.o")));
                    }
                });
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
