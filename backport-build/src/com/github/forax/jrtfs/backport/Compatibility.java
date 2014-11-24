package com.github.forax.jrtfs.backport;

import static org.objectweb.asm.Opcodes.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.jar.JarFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

public class Compatibility {
  class ClassMirror {
    final String name;
    final boolean isAPI;
    final ArrayList<String> superTypes = new ArrayList<>();
    final ArrayList<String> methods = new ArrayList<>();
    
    ClassMirror(String name, boolean isAPI) {
      this.name = name;
      this.isAPI = isAPI;
    }
    
    void writeSignatures(String className, BufferedWriter writer) throws IOException {
      for(String superType: superTypes) {
        ClassMirror superTypeMirror = classMirror(superType);
        if (superTypeMirror != null) {
          superTypeMirror.writeSignatures(className, writer);
        }
      }
      for(String method: methods) {
        writer.write(className + '.' + method);
        writer.newLine();
      }
    }
  }
  
  final LinkedHashMap<String,ClassMirror> classMirrorMap = new LinkedHashMap<>();
  
  ClassMirror classMirror(String className) {
    ClassMirror classMirror = classMirrorMap.get(className);
    if (classMirror == null) {
      System.out.println("unknown class " + className);
      return null;
    }
    return classMirror;
  }
  
  void parseClass(ClassReader reader) {
    reader.accept(new ClassVisitor(ASM5) {
      private ClassMirror classMirror;
      
      @Override
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        boolean isAPI = !name.startsWith("com") && !name.startsWith("sun") && (access & ACC_PUBLIC) != 0;
        ClassMirror classMirror = new ClassMirror(name, isAPI);
        if (superName != null) {
          classMirror.superTypes.add(superName);
        }
        Arrays.stream(interfaces).forEach(interfaze -> classMirror.superTypes.add(interfaze));
        classMirrorMap.put(name, classMirror);
        this.classMirror = classMirror;
      }
      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if ((access & (ACC_PUBLIC|ACC_PROTECTED)) == 0) {
          return null;
        }
        classMirror.methods.add(name + desc);
        return null;
      }
    }, ClassReader.SKIP_CODE);
  }
  
  public static void main(String[] args) throws IOException {
    Path input = Paths.get(args[0]);
    Path output = Paths.get(args[1]);
    Compatibility compatibility = new Compatibility();
    
    /*
    if (args.length == 3) {
      Path jdkSig = Paths.get(args[2]);
      Files.lines(jdkSig)
           .forEach(line -> {
             String[] parts = line.split("\\.");
             if (parts.length != 2) {
               throw new IllegalStateException("line " + line + " " + Arrays.toString(parts));
             }
             String className = parts[0];
             String method = parts[1];
             ClassMirror classMirror =
                 compatibility.classMirrorMap.computeIfAbsent(className,
                     key -> compatibility.new ClassMirror(key, true));
             classMirror.methods.add(method);
           });
    }*/
    
    
    try(JarFile jarInput = new JarFile(input.toFile());
        BufferedWriter writer = Files.newBufferedWriter(output)) {
      jarInput.stream()
              .forEach(entry -> {
                 try(InputStream inputStream = jarInput.getInputStream(entry)) {
                   String name = entry.getName();
                   if (name.endsWith(".class")) {
                     ClassReader reader = new ClassReader(inputStream);
                     compatibility.parseClass(reader);
                   }
                 } catch (IOException e) {
                   throw new UncheckedIOException(e);
                 }
              });
      
      for(ClassMirror classMirror: compatibility.classMirrorMap.values()) {
        if (classMirror.isAPI) {
          classMirror.writeSignatures(classMirror.name, writer);
        }
      }
    }
  }
}
