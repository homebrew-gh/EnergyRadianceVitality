//! ERV Nostr d-tag namespace (kind 30078), matching the Android app.

pub const WEIGHT_EXERCISES_D_TAG: &str = "erv/weight/exercises";
pub const WEIGHT_ROUTINES_D_TAG: &str = "erv/weight/routines";
pub const PROGRAMS_MASTER_D_TAG: &str = "erv/programs/master";
pub const CARDIO_ROUTINES_D_TAG: &str = "erv/cardio/routines";
pub const STRETCHING_ROUTINES_D_TAG: &str = "erv/stretching/routines";

pub const WEIGHT_CATALOG_D_TAG: &str = "erv/catalog/weight";
pub const STRETCH_CATALOG_D_TAG: &str = "erv/catalog/stretch";
pub const CARDIO_CATALOG_D_TAG: &str = "erv/catalog/cardio";

/// Whether a d-tag belongs to ERV encrypted app data.
pub fn is_erv_d_tag(d_tag: &str) -> bool {
    let d_tag = d_tag.trim();
    d_tag.starts_with("erv/")
}

/// Built-in catalogs published by Android; read-only on the web companion.
pub fn is_erv_catalog_d_tag(d_tag: &str) -> bool {
    let d_tag = d_tag.trim();
    d_tag == WEIGHT_CATALOG_D_TAG
        || d_tag == STRETCH_CATALOG_D_TAG
        || d_tag == CARDIO_CATALOG_D_TAG
}

/// Whether the web companion may publish this d-tag in v1.
pub fn is_erv_publishable_d_tag(d_tag: &str) -> bool {
    let d_tag = d_tag.trim();
    d_tag == WEIGHT_EXERCISES_D_TAG
        || d_tag == WEIGHT_ROUTINES_D_TAG
        || d_tag == PROGRAMS_MASTER_D_TAG
        || d_tag == CARDIO_ROUTINES_D_TAG
        || d_tag == STRETCHING_ROUTINES_D_TAG
        || is_erv_catalog_d_tag(d_tag)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn recognizes_erv_tags() {
        assert!(is_erv_d_tag("erv/weight/routines"));
        assert!(is_erv_d_tag("erv/weight/2026-06-13"));
        assert!(!is_erv_d_tag("fiatlife/budget"));
    }

    #[test]
    fn publishable_v1_tags() {
        assert!(is_erv_publishable_d_tag(WEIGHT_ROUTINES_D_TAG));
        assert!(is_erv_publishable_d_tag(STRETCHING_ROUTINES_D_TAG));
        assert!(!is_erv_publishable_d_tag("erv/weight/2026-06-13"));
    }

    #[test]
    fn catalog_tags_are_readable_and_publishable() {
        assert!(is_erv_catalog_d_tag(WEIGHT_CATALOG_D_TAG));
        assert!(is_erv_d_tag(STRETCH_CATALOG_D_TAG));
        assert!(is_erv_publishable_d_tag(WEIGHT_CATALOG_D_TAG));
    }
}
