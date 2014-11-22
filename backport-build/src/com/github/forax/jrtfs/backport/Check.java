package com.github.forax.jrtfs.backport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public class Check {
  public static void main(String[] args) throws IOException {
    Files.walk(Paths.get("output/classes"))
         .filter(path -> !Files.isDirectory(path))
         .forEach(path -> {
           ClassReader reader;
          try {
            reader = new ClassReader(Files.readAllBytes(path));
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
           reader.accept(new ClassVisitor(Opcodes.ASM5) {
             @Override
             public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
               if (version != Opcodes.V1_7) {
                 throw new AssertionError("version != 1.7 " + name);
               }
             }
          }, 0);
         });
  }
}
