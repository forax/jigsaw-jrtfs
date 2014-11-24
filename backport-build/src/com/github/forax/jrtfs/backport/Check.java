package com.github.forax.jrtfs.backport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.zip.GZIPInputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class Check {
  public static void main(String[] args) throws IOException {
    Path sigPath = Paths.get("jdk7-sig.txt");
    Path sigGzipPath = Paths.get("jdk7-sig.txt.gz");
    
    HashSet<String> compatSignatures = new HashSet<>();
    if (Files.exists(sigGzipPath)) {
      try(InputStream input = Files.newInputStream(sigGzipPath);
          GZIPInputStream gzipInput = new GZIPInputStream(input);
          Reader r = new InputStreamReader(gzipInput);
          BufferedReader reader = new BufferedReader(r)) {
        reader.lines().forEach(compatSignatures::add);
      }
    } else {
      Files.lines(sigPath).forEach(compatSignatures::add);
    }
    
    Files.walk(Paths.get("output/classes"))
         .filter(path -> path.toString().endsWith(".class"))
         .forEach(path -> {
           ClassReader reader;
           try {
             reader = new ClassReader(Files.readAllBytes(path));
           } catch (IOException e) {
             throw new UncheckedIOException(e);
           } catch(Exception e) {
             System.err.println("error while reading " + path);
             throw new RuntimeException(e);
           }
           reader.accept(new ClassVisitor(Opcodes.ASM5) {
             String className;
             
             @Override
             public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
               if (version != Opcodes.V1_7) {
                 throw new AssertionError("version != 1.7 " + name);
               }
               className = name;
             }
             
             @Override
             public MethodVisitor visitMethod(int access, String methodName, String methodDesc, String signature, String[] exceptions) {
               return new MethodVisitor(Opcodes.ASM5) {
                 @Override
                 public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                   if (owner.startsWith("jdk") || owner.startsWith("com") || owner.startsWith("sun") || owner.charAt(0) == '[') {
                     return;
                   }
                   String sig = owner + '.' + name + desc;
                   if (!(compatSignatures.contains(sig))) {
                     System.err.println("warning " + sig + " in " + className + '.' + methodName + methodDesc);
                   }
                 }
               };
             }
           }, 0);
         });
  }
}
