package tut.ac.za.AgriFinanceAPIs.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** A page of results with the total, so the UI can show "Load more" and "12 of 340". */
public record PageResult<T>(List<T> items, long total, int page, int size, boolean hasMore) {

    public static <E, T> PageResult<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResult<>(page.getContent().stream().map(mapper).toList(), page.getTotalElements(),
                page.getNumber(), page.getSize(), page.hasNext());
    }

    public static <T> PageResult<T> slice(List<T> all, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.max(1, Math.min(size, 200));
        int from = Math.min(all.size(), p * s);
        int to = Math.min(all.size(), from + s);
        return new PageResult<>(List.copyOf(all.subList(from, to)), all.size(), p, s, to < all.size());
    }
}
