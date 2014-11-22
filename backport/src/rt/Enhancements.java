package rt;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Enhancements {
  // --- entry points
  
  public static <T> Stream<T> stream(List<T> list) {
    return streamImpl(list);
  }
  public static <T> Stream<T> stream(Set<T> list) {
    return streamImpl(list);
  }
  public static <T> Function<T,T> identity() {
    return x -> x;
  }
  
  
  // --- implementations
  
  private static <T> StreamImpl<T> streamImpl(Iterable<? extends T> iterable) {
    return new StreamImpl<T>() {
      @Override
      public void forEach(Consumer<? super T> consumer) {
        for(T element: iterable) {
          consumer.accept(element);
        }
      }
    };
  }
  
  static abstract class StreamImpl<T> implements Stream<T> {
    @Override
    public abstract void forEach(Consumer<? super T> consumer);
    
    @Override
    public Stream<T> filter(Predicate<? super T> predicate) {
      return new StreamImpl<T>() {
        @Override
        public void forEach(Consumer<? super T> consumer) {
          StreamImpl.this.forEach(element -> {
            if (predicate.test(element)) {
              consumer.accept(element);
            }
          });
        }
      };
    }
    
    
    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Stream<T> sorted() {
      return sorted((e1, e2) -> ((Comparable)e1).compareTo(e2));
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <A> A[] toArray(IntFunction<A[]> generator) {
      List<T> list = collect(Collectors.toList());
      A[] array = generator.apply(list.size());
      Class<?> type = array.getClass().getComponentType();
      for(int i = 0; i < list.size(); i++) {
        array[i] = (A)type.cast(list.get(i));
      }
      return array;
    }
    
    @Override
    public <R, A> R collect(Collector<T, A, R> collector) {
      return collector.doCollect(this);
    }
    
    @Override
    public <U> Stream<U> map(Function<? super T, ? extends U> mapper) {
      return new StreamImpl<U>() {
        @Override
        public void forEach(Consumer<? super U> consumer) {
          StreamImpl.this.forEach(element -> {
            consumer.accept(mapper.apply(element));
          });
        }
      };
    }
    
    @Override
    public Stream<T> distinct() {
      return stream(collect(Collectors.toSet()));
    }
    
    @Override
    public Stream<T> sorted(Comparator<? super T> comparator) {
      return new StreamImpl<T>() {
        @Override
        public void forEach(Consumer<? super T> consumer) {
          List<T> list = StreamImpl.this.collect(Collectors.toList());
          Collections.sort(list, comparator);
          for(T element: list) {
            consumer.accept(element);
          }
        }
      };
    }
  }
  
  
  // --- lambda metafactory
  
  private static final MethodHandle PROXY;
  private static final MethodHandle INSERT_ARGUMENTS;
  static {
    Lookup lookup = MethodHandles.lookup();
    try {
      PROXY = lookup.findStatic(Enhancements.class, "proxy",
          MethodType.methodType(Object.class, Class.class, MethodHandle.class));
      INSERT_ARGUMENTS =  lookup.findStatic(Enhancements.class, "insertArguments",
                  MethodType.methodType(MethodHandle.class, MethodHandle.class, Object[].class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }
  
  // can not create a method handle on a method of java.lang.invoke, need a trampoline
  private static Object proxy(Class<?> interfaze, MethodHandle target) {
    return MethodHandleProxies.asInterfaceInstance(interfaze, target);
  }
  @SuppressWarnings("unused")
  private static MethodHandle insertArguments(MethodHandle target, Object... values) {
    return MethodHandles.insertArguments(target, 0, values);
  }
  
  public static CallSite metafactory(Lookup lookup, String name, MethodType type,
      MethodType sig, MethodHandle impl, MethodType reifiedSig) throws Throwable {

    Class<?> interfaze = type.returnType();
    if (type.parameterCount() == 0) {   // constant lambda
      MethodHandle target = impl.asType(reifiedSig);
      Object proxy = proxy(interfaze, target);
      return new ConstantCallSite(MethodHandles.constant(interfaze, proxy));
    }
    
    MethodHandle binder = INSERT_ARGUMENTS
        .bindTo(impl)
        .asCollector(Object[].class, type.parameterCount())
        .asType(type.changeReturnType(MethodHandle.class));
    
    MethodHandle proxyFactory = MethodHandles
        .dropArguments(PROXY.bindTo(interfaze), 1, type.parameterList());
    
    MethodHandle target = MethodHandles
        .foldArguments(proxyFactory, binder)
        .asType(type);
    
    return new ConstantCallSite(target);
  }
}
