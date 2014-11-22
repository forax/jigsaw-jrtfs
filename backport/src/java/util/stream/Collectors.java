package java.util.stream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class Collectors {
  public static <T, K, V> Collector<T, ?, Map<K,V>> toMap(Function<? super T, ? extends K> keyMapper,
                                                          Function<? super T, ? extends V> valueMapper) {
    return stream -> {
      HashMap<K, V> map = new HashMap<>();
      stream.forEach(element -> {
        map.put(keyMapper.apply(element), valueMapper.apply(element));
      });
      return map;
    };
  }

  public static <T> Collector<T, ?, Set<T>> toSet() {
    return toCollection(HashSet::new);
  }
  
  public static <T> Collector<T, ?, List<T>> toList() {
    return toCollection(ArrayList::new);
  }
  
  private static <T, C extends Collection<T>> Collector<T, ?, C> toCollection(Supplier<C> collectionFactory) {
    return stream -> {
      C collection = collectionFactory.get();
      stream.forEach(collection::add);
      return collection;
    };
  }
}
