package dev.mindforge.domain.model;

/**
 * One change a conversation edit asks for. Only an edit can hold a {@link Delete} or a {@link Retitle}, so an
 * uploaded document cannot delete or retitle a page — by type (T15).
 */
public sealed interface EditItem permits Claim, EditItem.Delete, EditItem.Retitle {

    record Delete(String path) implements EditItem {}

    record Retitle(String path, String title) implements EditItem {}
}
