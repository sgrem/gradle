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

package org.gradle.nativeplatform.test.xctest

import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.IncrementalSwiftXCTestAddDiscoveryBundle
import org.gradle.nativeplatform.fixtures.app.IncrementalSwiftXCTestRemoveDiscoveryBundle
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithXCTest
import org.gradle.nativeplatform.fixtures.app.SwiftFailingXCTestBundle
import org.gradle.nativeplatform.fixtures.app.SwiftLib
import org.gradle.nativeplatform.fixtures.app.SwiftLibTest
import org.gradle.nativeplatform.fixtures.app.SwiftLibWithXCTest
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Unroll

@Requires([TestPrecondition.SWIFT_SUPPORT, TestPrecondition.MAC_OS_X])
class SwiftXcodeXCTestIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    String getRootProjectName() {
        'Foo'
    }

    def setup() {
        settingsFile << """
rootProject.name = '$rootProjectName'
"""
        buildFile << """
apply plugin: XcodeXCTestConventionPlugin
"""
    }

    def "executes check lifecycle task only when no library or executable components"() {
        when:
        succeeds("check")

        then:
        result.assertTasksExecuted(":check")
        result.assertTasksSkipped(":check")
    }

    def "fails to find test task when no library or executable components"() {
        when:
        def result = fails("test")

        then:
        result.assertHasDescription("Task 'test' not found in root project '$rootProjectName'")
    }

    def "fails when test cases fail"() {
        def testBundle = new SwiftFailingXCTestBundle()

        given:
        settingsFile << "rootProject.name = 'app'"
        buildFile << "apply plugin: 'swift-library'"
        testBundle.writeToProject(testDirectory)

        when:
        fails("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest")
        testBundle.assertTestCasesRan(output)
    }

    def "succeeds when test cases pass"() {
        def lib = new SwiftLibWithXCTest().inProject(rootProjectName)

        given:
        buildFile << "apply plugin: 'swift-library'"
        lib.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        lib.assertTestCasesRan(output)
    }

    def "can build xctest bundle when Info.plist is provided"() {
        def lib = new SwiftLibWithXCTest().withInfoPlist().inProject(rootProjectName)

        given:
        buildFile << "apply plugin: 'swift-library'"
        lib.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        lib.assertTestCasesRan(output)
    }

    @Unroll
    def "runs tests when #task lifecycle task executes"() {
        def lib = new SwiftLibWithXCTest().inProject(rootProjectName)

        given:
        buildFile << "apply plugin: 'swift-library'"
        lib.writeToProject(testDirectory)

        when:
        succeeds(task)

        then:
        executed(":xcTest")
        lib.assertTestCasesRan(output)

        where:
        task << ["test", "check", "build"]
    }

    def "can test public and internal features of a Swift library"() {
        def lib = new SwiftLibWithXCTest().inProject(rootProjectName)

        given:
        buildFile << """
apply plugin: 'swift-library'
"""
        lib.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        lib.assertTestCasesRan(output)
    }

    def "does not execute removed test suite and case"() {
        def testBundle = new IncrementalSwiftXCTestRemoveDiscoveryBundle()

        given:
        buildFile << "apply plugin: 'swift-library'"
        testBundle.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        testBundle.expectedSummaryOutputPattern.matcher(output).find()

        when:
        testBundle.applyChangesToProject(testDirectory)
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        testBundle.expectedAlternateSummaryOutputPattern.matcher(output).find()
    }

    def "executes added test suite and case"() {
        def testBundle = new IncrementalSwiftXCTestAddDiscoveryBundle()

        given:
        buildFile << "apply plugin: 'swift-library'"
        testBundle.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        testBundle.expectedSummaryOutputPattern.matcher(output).find()

        when:
        testBundle.applyChangesToProject(testDirectory)
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        testBundle.expectedAlternateSummaryOutputPattern.matcher(output).find()
    }

    def "skips test tasks as up-to-date when nothing changes between invocation"() {
        def lib = new SwiftLibWithXCTest().inProject(rootProjectName)

        given:
        buildFile << "apply plugin: 'swift-library'"
        lib.writeToProject(testDirectory)

        when:
        succeeds("test")
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        result.assertTasksSkipped(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
    }

    def "build logic can change source layout convention"() {
        def lib = new SwiftLibWithXCTest().inProject(rootProjectName)

        given:
        buildFile << "apply plugin: 'swift-library'"
        lib.main.writeToSourceDir(file("Sources"))
        lib.test.writeToSourceDir(file("Tests"))
        file("src/main/swift/broken.swift") << "ignore me!"
        file("src/test/swift/broken.swift") << "ignore me!"

        and:
        buildFile << """
            library {
                source.from 'Sources'
            }
            xctest {
                source.from 'Tests'
                resourceDir.set(file('Tests'))
            }
         """

        expect:
        succeeds "test"
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")

        file("build/obj/test").assertIsDir()
        executable("build/exe/test/${rootProjectName}Test").assertExists()
        lib.assertTestCasesRan(output)
    }

    def "can specify a test dependency on another library"() {
        def lib = new SwiftLib()
        def test = new SwiftLibTest(lib.greeter, lib.sum, lib.multiply).asModule(rootProjectName).withImport("Greeter")

        given:
        settingsFile << """
include 'greeter'
"""
        buildFile << """
project(':greeter') {
    apply plugin: 'swift-library'
}

apply plugin: 'swift-library'

dependencies {
    testImplementation project(':greeter')
}
"""
        lib.asModule("Greeter").writeToProject(file('greeter'))
        test.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":greeter:compileDebugSwift", ":greeter:linkDebug", ":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
    }

    def "does not build or run any of the tests when assemble task executes"() {
        def testBundle = new SwiftFailingXCTestBundle()

        given:
        testBundle.writeToProject(testDirectory)

        when:
        succeeds("assemble")

        then:
        result.assertTasksExecuted(":assemble")
        result.assertTasksSkipped(":assemble")
    }

    def "skips test tasks when no source is available for Swift library"() {
        given:
        buildFile << "apply plugin: 'swift-library'"

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        result.assertTasksSkipped(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
    }

    def "skips test tasks when no source is available for Swift executable"() {
        given:
        buildFile << """
apply plugin: 'swift-executable'
"""

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        result.assertTasksSkipped(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
    }

    def "can test public and internal features of a Swift executable"() {
        def app = new SwiftAppWithXCTest()

        given:
        settingsFile << "rootProject.name = 'app'"
        buildFile << """
apply plugin: 'swift-executable'

linkTest.source = project.files(new HashSet(linkTest.source.from)).filter { !it.name.equals("main.o") }
"""
        app.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
    }
}
