import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import sun.misc.Unsafe;

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
    //System.out.println(FileSystemProvider.installedProviders());
    
    String RUNTIME_HOME = "/usr/jdk/jdk1.9.0-jigsaw";
    
    // we need to patch the home at runtime
    FileSystem defaultFs = FileSystems.getDefault();
    Path bootImagePath = defaultFs.getPath(RUNTIME_HOME, "lib", "modules", "bootmodules.jimage");
    Path extImagePath = defaultFs.getPath(RUNTIME_HOME, "lib", "modules", "extmodules.jimage");
    Path appImagePath = defaultFs.getPath(RUNTIME_HOME, "lib", "modules", "appmodules.jimage");
    patch(bootImagePath, extImagePath, appImagePath);
    
    FileSystem jrtFs = FileSystems.getFileSystem(new URI("jrt:/"));
    for(Path root: jrtFs.getRootDirectories()) {
      System.out.println(root);
      
      Files.walkFileTree(root, new FileVisitor<Path>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          System.out.println("found " + file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          return FileVisitResult.CONTINUE;
        }
      });
      
      /*
      try(DirectoryStream<Path> directoryStream = Files.newDirectoryStream(root)) {
        for(Path path: directoryStream) {
          System.out.println(path);
        }
      }*/
    }
  }
}
