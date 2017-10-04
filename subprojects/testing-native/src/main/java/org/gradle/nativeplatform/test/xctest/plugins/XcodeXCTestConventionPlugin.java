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
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.provider.PropertyState;
import org.gradle.language.swift.SwiftApplication;
import org.gradle.language.swift.SwiftComponent;
import org.gradle.language.swift.SwiftLibrary;
import org.gradle.language.swift.plugins.SwiftBasePlugin;
import org.gradle.nativeplatform.test.xctest.SwiftXCTestSuite;
import org.gradle.nativeplatform.test.xctest.internal.DefaultSwiftXcodeXCTestSuite;
import org.gradle.util.GUtil;

/**
 * A plugin that sets up the infrastructure for testing native binaries with XCTest test framework using Xcode runtime.
 * It also adds conventions on top of it.
 *
 * @since 4.4
 */
@Incubating
public class XcodeXCTestConventionPlugin implements Plugin<ProjectInternal> {
    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(SwiftBasePlugin.class);
        project.getPluginManager().apply(XcodeXCTestPlugin.class);

        project.getComponents().all(new Action<SoftwareComponent>() {
            @Override
            public void execute(SoftwareComponent component) {
                if (component instanceof SwiftApplication || component instanceof SwiftLibrary) {
                    createTestSuite((SwiftComponent) component, project);
                }
            }
        });
    }

    private static void createTestSuite(SwiftComponent testedComponent, Project project) {
        String name = testedComponent.getName() + "Test";
        if (testedComponent.getName().equals("main")) {
            name = "test";
        }

        // TODO - Reuse logic from Swift*Plugin
        // TODO - component name and extension name aren't the same
        // TODO - should use `src/xctext/swift` as the convention?
        // Add the component extension
        SwiftXCTestSuite testSuite = project.getObjects().newInstance(DefaultSwiftXcodeXCTestSuite.class,
            name, project.getConfigurations(), testedComponent);
        project.getExtensions().add(SwiftXCTestSuite.class, "xctest", testSuite);
        project.getComponents().add(testSuite);
        project.getComponents().add(testSuite.getDevelopmentBinary());

        // Setup component
        final PropertyState<String> module = testSuite.getModule();
        module.set(GUtil.toCamelCase(project.getName() + "Test"));
    }
}
