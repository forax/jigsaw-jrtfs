package com.github.forax.jrtfs.backport;

import static org.objectweb.asm.Opcodes.ASM5;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

import java.io.IOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.stream.Collectors;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.RemappingClassAdapter;


public class RetroWeaver {
  // new classes that needs to be moved in a new package (out of java.lang)
  final HashSet<String> rtClasses = new HashSet<>();
  
  public void addNewClass(String internalName) {
    rtClasses.add(internalName);
  }
  
  public byte[] retroweave(byte[] source) {
    ClassReader reader = new ClassReader(source);
    ClassWriter writer = new ClassWriter(reader, 0);
    reader.accept(new ClassVisitor(ASM5,
        new RemappingClassAdapter(writer,
          new Remapper() {
            @Override
            public String map(String typeName) {
              if (rtClasses.contains(typeName)) {
                return "com/github/forax/jrtfs/backport/" + typeName;
              }
              return typeName;
            }
          })) {
      @Override
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        if (version != Opcodes.V1_7) {   // downgrade to 1.7
          version = Opcodes.V1_7;
        }
        super.visit(version, access, name, signature, superName, interfaces);
      }
      
      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        return new MethodVisitor(ASM5, mv) {
          @Override
          public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            switch(owner + '.' + name) {  // retarget default methods/static methods of Java 8 
            case "java/util/List.stream":
            case "java/util/Set.stream":
              super.visitMethodInsn(INVOKESTATIC, "com/github/forax/jrtfs/backport/rt/Enhancements", "stream", "(L" + owner + ";)Ljava/util/stream/Stream;", false);
              return;
            case "java/util/function/Function.identity":
              super.visitMethodInsn(INVOKESTATIC, "com/github/forax/jrtfs/backport/rt/Enhancements", "identity", "()Ljava/util/function/Function;", false);
              return;
            default:
              super.visitMethodInsn(opcode, owner, name, desc, itf);
              return;
            }
          }
          
          @Override
          public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
            if (bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")) {  // this is a lambda callsite
              super.visitInvokeDynamicInsn(name, desc, RETRO_BSM, bsmArgs); 
              return;
            }
            throw new IllegalStateException("invalid invokedynamic call");
          }
        };
      }
    }, ClassReader.EXPAND_FRAMES);
    return writer.toByteArray();
  }
  
  static Handle RETRO_BSM = new Handle(Opcodes.H_INVOKESTATIC,
      "com/github/forax/jrtfs/backport/rt/Enhancements",
      "metafactory",
      MethodType.methodType(CallSite.class, Lookup.class, String.class, MethodType.class,
                                            MethodType.class, MethodHandle.class, MethodType.class
                           ).toMethodDescriptorString());
  
  public static void main(String[] args) throws IOException {
    Path backportClasses = Paths.get("output/backport/classes");
    Path retroClasses = Paths.get("output/retro/classes");
    Path outputClasses = Paths.get("output/classes");
    
    RetroWeaver retroWeaver = new RetroWeaver();
    
    // add classes
    Files.walk(backportClasses)
         .forEach(path -> {
           Path localPath = backportClasses.relativize(path);
           String filename = localPath.toString();
           
           if (filename.endsWith(".class")) {
             String internalName = filename.substring(0, filename.length() - 6);
             System.out.println("need to repackage " + internalName);
             retroWeaver.addNewClass(internalName);
           }
         });
    
    // retro weave
    for(Path path: Files.walk(retroClasses).filter(path -> !Files.isDirectory(path)).collect(Collectors.toList())) {
      byte[] source = Files.readAllBytes(path);
      byte[] result = retroWeaver.retroweave(source);
      System.out.println("retro " + path);
      //System.out.println(retroClasses.relativize(path));
      //System.out.println(outputClasses.resolve(retroClasses.relativize(path)));
      Path destPath = outputClasses.resolve(retroClasses.relativize(path));
      Files.createDirectories(destPath.getParent());
      Files.write(destPath, result);
    }
  }
}
