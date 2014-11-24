jigsaw-jrtfs
============

A backport of jrtfs.jar for 1.7

This backport automatically take the source from JDK 9 that are related to the JRT filesystem
and retrofit them to be used by a Java 7 VM.

Warning: all the files of the directory 'src' are under the OpenJDK classical license (GPL + ClassPath Exception)
while all the other files are under the MIT license.

To read the jigsaw image format you need to put the jrtfs-backport.jar in your classpath *and*
at runtime change 3 global properties

```java
public class Test {
  private static void patch(Path bootImagePath, Path extImagePath, Path appImagePath) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
    Class<?> systemImagesClass = Class.forName("jdk.internal.jrtfs.SystemImages");
    Field bootImagePathField = systemImagesClass.getDeclaredField("bootImagePath");
    Field extImagePathField = systemImagesClass.getDeclaredField("extImagePath");
    Field appImagePathField = systemImagesClass.getDeclaredField("appImagePath");
    
    Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
    theUnsafe.setAccessible(true);
    Unsafe unsafe = (Unsafe)theUnsafe.get(null);
    
    unsafe.putObjectVolatile(unsafe.staticFieldBase(bootImagePathField), unsafe.staticFieldOffset(bootImagePathField), bootImagePath);
    unsafe.putObjectVolatile(unsafe.staticFieldBase(extImagePathField), unsafe.staticFieldOffset(extImagePathField), extImagePath);
    unsafe.putObjectVolatile(unsafe.staticFieldBase(appImagePathField), unsafe.staticFieldOffset(appImagePathField), appImagePath);
  }
  
  public static void main(String[] args) throws URISyntaxException, ClassNotFoundException, NoSuchFieldException, IllegalAccessException, IOException {
    String RUNTIME_HOME = "/usr/jdk/jdk1.9.0-jigsaw";  // your JAVA HOME containing the JDK 9
    
    // we need to patch the home at runtime
    FileSystem defaultFs = FileSystems.getDefault();
    Path bootImagePath = defaultFs.getPath(RUNTIME_HOME, "lib", "modules", "bootmodules.jimage");
    Path extImagePath = defaultFs.getPath(RUNTIME_HOME, "lib", "modules", "extmodules.jimage");
    Path appImagePath = defaultFs.getPath(RUNTIME_HOME, "lib", "modules", "appmodules.jimage");
    patch(bootImagePath, extImagePath, appImagePath);
    
    FileSystem jrtFs = FileSystems.getFileSystem(new URI("jrt:/"));
    // enjoy !
  }
}
```
