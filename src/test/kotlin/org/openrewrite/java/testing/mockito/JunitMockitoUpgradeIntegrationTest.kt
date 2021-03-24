/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.mockito

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.openrewrite.*
import org.openrewrite.config.Environment
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest
import org.openrewrite.maven.MavenParser

/**
 * Validates the recipes related to upgrading from Mockito 1 to Mockito 3
 */
class JunitMockitoUpgradeIntegrationTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .logCompilationWarningsAndErrors(true)
        .classpath("mockito-all", "junit", "hamcrest")
        .build()

    override val recipe: Recipe = Environment.builder()
        .scanClasspath(emptyList())
        .build()
        .activateRecipes("org.openrewrite.java.testing.junit5.JUnit4to5Migration",
                "org.openrewrite.java.testing.mockito.Mockito1to3Migration")

    /**
     * Replace org.mockito.MockitoAnnotations.Mock with org.mockito.Mock
     */
    @Test
    fun replaceMockAnnotation() = assertChanged(
            before = """
                package org.openrewrite.java.testing.junit5;
                
                import org.junit.Before;
                import org.junit.Test;
                import org.mockito.Mock;
                import org.mockito.MockitoAnnotations;
                
                import java.util.List;
                
                import static org.mockito.Mockito.verify;
                
                public class MockitoTests {
                    @Mock
                    List<String> mockedList;
                
                    @Before
                    public void initMocks() {
                        MockitoAnnotations.initMocks(this);
                    }
                
                    @Test
                    public void usingAnnotationBasedMock() {
                
                        mockedList.add("one");
                        mockedList.clear();
                
                        verify(mockedList).add("one");
                        verify(mockedList).clear();
                    }
                }
            """,
            after = """
                package org.openrewrite.java.testing.junit5;
                
                import org.junit.jupiter.api.BeforeEach;
                import org.junit.jupiter.api.Test;
                import org.mockito.Mock;
                import org.mockito.MockitoAnnotations;
                
                import java.util.List;
                
                import static org.mockito.Mockito.verify;
                
                public class MockitoTests {
                    @Mock
                    List<String> mockedList;
                
                    @BeforeEach
                    void initMocks() {
                        MockitoAnnotations.initMocks(this);
                    }
                
                    @Test
                    void usingAnnotationBasedMock() {
                
                        mockedList.add("one");
                        mockedList.clear();
                
                        verify(mockedList).add("one");
                        verify(mockedList).clear();
                    }
                }
            """
    )

    /**
     * Replaces org.mockito.Matchers with org.mockito.ArgumentMatchers
     */
    @Test
    fun replacesMatchers() = assertUnchanged(
            before = """
                package mockito.example;
                
                import java.util.List;
                
                import static org.mockito.Mockito.*;
                
                public class MockitoArgumentMatchersTest {
                    static class Foo {
                        boolean bool(String str, int i, Object obj) { return false; }
                        int in(boolean b, List<String> strs) { return 0; }
                        int bar(byte[] bytes, String[] s, int i) { return 0; }
                        boolean baz(String ... strings) { return true; }
                    }
                
                    public void usesMatchers() {
                        Foo mockFoo = mock(Foo.class);
                        when(mockFoo.bool(anyString(), anyInt(), any(Object.class))).thenReturn(true);
                        when(mockFoo.bool(eq("false"), anyInt(), any(Object.class))).thenReturn(false);
                        when(mockFoo.in(anyBoolean(), anyList())).thenReturn(10);
                    }
                }
            """
    )

    /**
     * Mockito 1 used Matchers.anyVararg() to match the arguments to a variadic function.
     * Mockito 2+ uses Matchers.any() to match anything including the arguments to a variadic function.
     */
    @Test
    fun replacesAnyVararg() = assertChanged(
            before = """
                package mockito.example;
    
                import static org.mockito.Matchers.anyVararg;
                import static org.mockito.Mockito.mock;
                import static org.mockito.Mockito.when;
                
                public class MockitoVarargMatcherTest {
                    public static class Foo {
                        public boolean acceptsVarargs(String ... strings) { return true; }
                    }
                    public void usesVarargMatcher() {
                        Foo mockFoo = mock(Foo.class);
                        when(mockFoo.acceptsVarargs(anyVararg())).thenReturn(true);
                    }
                }
            """,
            after = """
                package mockito.example;

                import static org.mockito.ArgumentMatchers.any;
                import static org.mockito.Mockito.mock;
                import static org.mockito.Mockito.when;
                
                public class MockitoVarargMatcherTest {
                    public static class Foo {
                        public boolean acceptsVarargs(String ... strings) { return true; }
                    }
                    public void usesVarargMatcher() {
                        Foo mockFoo = mock(Foo.class);
                        when(mockFoo.acceptsVarargs(any())).thenReturn(true);
                    }
                }
            """
    )

    /**
     * Mockito 1 has InvocationOnMock.getArgumentAt(int, Class)
     * Mockito 3 has InvocationOnMock.getArgument(int, Class)
     * swap 'em
     */
    @Test
    fun replacesGetArgumentAt() = assertChanged(
            before = """
                package mockito.example;

                import org.junit.jupiter.api.Test;
                
                import static org.mockito.Matchers.any;
                import static org.mockito.Mockito.mock;
                import static org.mockito.Mockito.when;
                
                public class MockitoDoAnswer {
                    @Test
                    public void aTest() {
                        String foo = mock(String.class);
                        when(foo.concat(any())).then(invocation -> invocation.getArgumentAt(0, String.class));
                    }
                }
            """,
            after = """
                package mockito.example;

                import org.junit.jupiter.api.Test;
                
                import static org.mockito.ArgumentMatchers.any;
                import static org.mockito.Mockito.mock;
                import static org.mockito.Mockito.when;
                
                public class MockitoDoAnswer {
                    @Test
                    public void aTest() {
                        String foo = mock(String.class);
                        when(foo.concat(any())).then(invocation -> invocation.getArgument(0, String.class));
                    }
                }
            """
    )

    @Test
    fun removesRunWithJunit4() = assertChanged(
        before = """
            import org.junit.runner.RunWith;
            import org.junit.runners.JUnit4;
            
            @RunWith(JUnit4.class)
            public class Foo {
            }
        """,
        after = """
            
            public class Foo {
            }
        """
    )

    @Test
    fun replacesMockitoJUnitRunner() = assertChanged(
            before = """
                import org.junit.runner.RunWith;
                import org.mockito.runners.MockitoJUnitRunner;
                
                @RunWith(MockitoJUnitRunner.class)
                public class ExampleTest { }
            """,
            after = """
                import org.junit.jupiter.api.extension.ExtendWith;
                import org.mockito.junit.jupiter.MockitoExtension;
            
                @ExtendWith(MockitoExtension.class)
                public class ExampleTest { }
            """
    )

    val exampleJunitBefore = """
        package org.openrewrite.java.testing.junit5;
        
        import org.junit.AfterClass;
        import org.junit.Assert;
        import org.junit.Before;
        import org.junit.BeforeClass;
        import org.junit.Test;
        import org.junit.Test;
        import org.junit.rules.ExpectedException;
        import org.junit.rules.TemporaryFolder;
        import org.junit.rules.Timeout;
        import org.mockito.Mock;
        import org.mockito.MockitoAnnotations;
        
        import java.io.File;
        import java.io.IOException;
        import java.util.List;
        
        import static org.mockito.Mockito.*;
        
        public class ExampleJunitTestClass {
        
            @Rule
            public TemporaryFolder folder = new TemporaryFolder();
        
            @Before
            public void beforeClass() {
                MockitoAnnotations.initMocks(this);
            }
        
            @AfterClass
            public static void afterClass() { }
        
            @Mock
            List<String> mockedList;
        
            public void initMocks() {
                MockitoAnnotations.initMocks(this);
            }
        
            @Test
            public void usingAnnotationBasedMock() {
                mockedList.add("one");
                mockedList.clear();
        
                verify(mockedList).add("one");
                verify(mockedList).clear();
            }
        
            @Test(expected = RuntimeException.class)
            public void foo() throws IOException {
                File tempFile = folder.newFile();
                File tempFile2 = folder.newFile("filename");
                File tempDir = folder.getRoot();
                File tempDir2 = folder.newFolder("parent", "child");
                File tempDir3 = folder.newFolder("subdir");
                File tempDir4 = folder.newFolder();
                String foo = "foo";
                throw new RuntimeException(foo);
            }
        
            @Test(expected = IndexOutOfBoundsException.class)
            public void foo2() {
                int arr = new int[]{}[0];
            }
        
            @Rule
            public ExpectedException throwz = ExpectedException.none();
        
            @Test
            public void foo3() {
                throwz.expect(RuntimeException.class);
                throw new RuntimeException();
            }
        
            @Test
            public void assertsStuff() {
                Assert.assertEquals("One is one", 1, 1);
                Assert.assertArrayEquals("Empty is empty", new int[]{}, new int[]{});
                Assert.assertNotEquals("one is not two", 1, 2);
                Assert.assertFalse("false is false", false);
                Assert.assertTrue("true is true", true);
                Assert.assertEquals("foo is foo", "foo", "foo");
                Assert.assertNull("null is null", null);
                Assert.fail("fail");
            }
        
            @Test(timeout = 500)
            public void bar() { }
        
            @Test
            public void aTest() {
                String foo = mock(String.class);
                when(foo.concat(any())).then(invocation -> invocation.getArgumentAt(0, String.class));
            }
        }
        """.trimIndent()
    val exampleJunitAfter = """
        package org.openrewrite.java.testing.junit5;

        import org.junit.jupiter.api.*;
        import org.junit.jupiter.api.io.TempDir;
        import org.mockito.Mock;
        import org.mockito.MockitoAnnotations;
        
        import java.io.File;
        import java.io.IOException;
        import java.nio.file.Files;
        import java.util.List;
        
        import static org.junit.jupiter.api.Assertions.assertThrows;
        import static org.mockito.Mockito.*;
        
        public class ExampleJunitTestClass {
        
            @TempDir
            File folder;
        
            @BeforeEach
            void beforeClass() {
                MockitoAnnotations.initMocks(this);
            }
        
            @AfterAll
            static void afterClass() {
            }
        
            @Mock
            List<String> mockedList;
        
            public void initMocks() {
                MockitoAnnotations.initMocks(this);
            }
        
            @Test
            void usingAnnotationBasedMock() {
                mockedList.add("one");
                mockedList.clear();
        
                verify(mockedList).add("one");
                verify(mockedList).clear();
            }
        
            @Test
            void foo() throws IOException {
                assertThrows(RuntimeException.class, () -> {
                    File tempFile = File.createTempFile("junit", null, folder);
                    File tempFile2 = newFile(folder, "filename");
                    File tempDir = folder;
                    File tempDir2 = newFolder(folder, "parent", "child");
                    File tempDir3 = newFolder(folder, "subdir");
                    File tempDir4 = Files.createTempDirectory(folder.toPath(), "junit").toFile();
                    String foo = "foo";
                    throw new RuntimeException(foo);
                });
            }
        
            @Test
            void foo2() {
                assertThrows(IndexOutOfBoundsException.class, () -> {
                    int arr = new int[]{}[0];
                });
            }
        
            @Test
            void foo3() {
                assertThrows(RuntimeException.class, () -> {
                    throw new RuntimeException();
                });
            }
        
            @Test
            void assertsStuff() {
                Assertions.assertEquals(1, 1, "One is one");
                Assertions.assertArrayEquals(new int[]{}, new int[]{}, "Empty is empty");
                Assertions.assertNotEquals(1, 2, "one is not two");
                Assertions.assertFalse(false, "false is false");
                Assertions.assertTrue(true, "true is true");
                Assertions.assertEquals("foo", "foo", "foo is foo");
                Assertions.assertNull(null, "null is null");
                Assertions.fail("fail");
            }
        
            @Test
            @Timeout(500)
            void bar() {
            }
        
            @Test
            void aTest() {
                String foo = mock(String.class);
                when(foo.concat(any())).then(invocation -> invocation.getArgument(0, String.class));
            }
        
            private static File newFile(File root, String fileName) throws IOException {
                File file = new File(root, fileName);
                file.createNewFile();
                return file;
            }
        
            private static File newFolder(File root, String ... folders) throws IOException {
                File result = new File(root, String.join("/", folders));
                if (!result.mkdirs()) {
                    throw new IOException("Couldn't create folders " + root);
                }
                return result;
            }
        }
    """.trimIndent()

    @Test
    fun theBigOne() {
        val javaSource = parser.parse(exampleJunitBefore)[0]
        val mavenSource = MavenParser.builder().build().parse("""
                    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                        <modelVersion>4.0.0</modelVersion>
                    
                        <groupId>org.openrewrite.example</groupId>
                        <artifactId>integration-testing</artifactId>
                        <version>1.0</version>
                        <name>integration-testing</name>
                    
                        <properties>
                            <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                            <java.version>1.8</java.version>
                        </properties>
                    
                        <dependencies>
                            <dependency>
                                <groupId>junit</groupId>
                                <artifactId>junit</artifactId>
                                <version>4.12</version>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>com.googlecode.json-simple</groupId>
                                <artifactId>json-simple</artifactId>
                                <version>1.1.1</version>
                            </dependency>
                        </dependencies>
                    </project>
                """.trimIndent())[0]

        val sources: List<SourceFile> = listOf(javaSource, mavenSource)

        val results = recipe.run(sources, InMemoryExecutionContext { error: Throwable -> throw error })

        val mavenResult = results.find { it.before === mavenSource }
        assertThat(mavenResult).isNotNull

        assertThat(mavenResult?.after?.print()).isEqualTo("""
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>

                <groupId>org.openrewrite.example</groupId>
                <artifactId>integration-testing</artifactId>
                <version>1.0</version>
                <name>integration-testing</name>

                <properties>
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    <java.version>1.8</java.version>
                </properties>
            
                <dependencies>
                    <dependency>
                        <groupId>com.googlecode.json-simple</groupId>
                        <artifactId>json-simple</artifactId>
                        <version>1.1.1</version>
                        <exclusions>
                            <exclusion>
                                <groupId>junit</groupId>
                                <artifactId>junit</artifactId>
                            </exclusion>
                        </exclusions>
                    </dependency>
                    <dependency>
                        <groupId>org.junit.jupiter</groupId>
                        <artifactId>junit-jupiter-api</artifactId>
                        <version>5.7.1</version>
                        <scope>test</scope>
                    </dependency>
                    <dependency>
                        <groupId>org.junit.jupiter</groupId>
                        <artifactId>junit-jupiter-engine</artifactId>
                        <version>5.7.1</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
            </project>
        """.trimIndent())

        val javaResult = results.find { it.before === javaSource }
        assertThat(javaResult).isNotNull
        assertThat(javaResult!!.after!!.printTrimmed()).isEqualTo(exampleJunitAfter)
    }

}
