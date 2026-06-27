//! ERV Nostr d-tag namespace (kind 30078), matching the Android app.

pub const WEIGHT_EXERCISES_D_TAG: &str = "erv/weight/exercises";
pub const WEIGHT_ROUTINES_D_TAG: &str = "erv/weight/routines";
pub const PROGRAMS_MASTER_D_TAG: &str = "erv/programs/master";
pub const WORKOUTS_LIBRARY_D_TAG: &str = "erv/workouts/library";
pub const CARDIO_ROUTINES_D_TAG: &str = "erv/cardio/routines";
pub const STRETCHING_ROUTINES_D_TAG: &str = "erv/stretching/routines";

pub const WEIGHT_CATALOG_D_TAG: &str = "erv/catalog/weight";
pub const STRETCH_CATALOG_D_TAG: &str = "erv/catalog/stretch";
pub const CARDIO_CATALOG_D_TAG: &str = "erv/catalog/cardio";
pub const FITNESS_EQUIPMENT_D_TAG: &str = "erv/equipment";
pub const TRAINING_PROFILE_D_TAG: &str = "erv/training-profile";
pub const MEDIA_LIBRARY_D_TAG: &str = "erv/media/library";

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

fn is_iso_date(date: &str) -> bool {
    let b = date.as_bytes();
    b.len() == 10
        && b[4] == b'-'
        && b[7] == b'-'
        && b[..4].iter().all(|c| c.is_ascii_digit())
        && b[5..7].iter().all(|c| c.is_ascii_digit())
        && b[8..10].iter().all(|c| c.is_ascii_digit())
}

/// `erv/weight/YYYY-MM-DD` day log tags (not exercises/routines masters).
pub fn weight_day_log_date(d_tag: &str) -> Option<&str> {
    const PREFIX: &str = "erv/weight/";
    let d_tag = d_tag.trim();
    if !d_tag.starts_with(PREFIX) {
        return None;
    }
    let suffix = &d_tag[PREFIX.len()..];
    if suffix == "exercises" || suffix == "routines" {
        return None;
    }
    let date = suffix.split("/session/").next().unwrap_or(suffix);
    if !is_iso_date(date) {
        return None;
    }
    Some(date)
}

/// `erv/cardio/YYYY-MM-DD` day log tags (not routines master).
pub fn cardio_day_log_date(d_tag: &str) -> Option<&str> {
    const PREFIX: &str = "erv/cardio/";
    let d_tag = d_tag.trim();
    if !d_tag.starts_with(PREFIX) || d_tag == CARDIO_ROUTINES_D_TAG {
        return None;
    }
    let suffix = &d_tag[PREFIX.len()..];
    if suffix == "routines" {
        return None;
    }
    let date = suffix.split("/session/").next().unwrap_or(suffix);
    if !is_iso_date(date) {
        return None;
    }
    Some(date)
}

pub fn is_training_day_log_d_tag(d_tag: &str) -> bool {
    weight_day_log_date(d_tag).is_some() || cardio_day_log_date(d_tag).is_some()
}

/// Whether the web companion may publish this d-tag in v1.
pub fn is_erv_publishable_d_tag(d_tag: &str) -> bool {
    let d_tag = d_tag.trim();
    d_tag == WEIGHT_EXERCISES_D_TAG
        || d_tag == WEIGHT_ROUTINES_D_TAG
        || d_tag == PROGRAMS_MASTER_D_TAG
        || d_tag == WORKOUTS_LIBRARY_D_TAG
        || d_tag == CARDIO_ROUTINES_D_TAG
        || d_tag == STRETCHING_ROUTINES_D_TAG
        || d_tag == FITNESS_EQUIPMENT_D_TAG
        || d_tag == TRAINING_PROFILE_D_TAG
        || d_tag == MEDIA_LIBRARY_D_TAG
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
    fn recognizes_split_training_day_tags() {
        assert_eq!(
            weight_day_log_date("erv/weight/2026-06-13/session/abc"),
            Some("2026-06-13")
        );
        assert_eq!(
            cardio_day_log_date("erv/cardio/2026-06-13/session/abc"),
            Some("2026-06-13")
        );
    }

    #[test]
    fn publishable_v1_tags() {
        assert!(is_erv_publishable_d_tag(WORKOUTS_LIBRARY_D_TAG));
        assert!(is_erv_publishable_d_tag(WEIGHT_ROUTINES_D_TAG));
        assert!(is_erv_publishable_d_tag(STRETCHING_ROUTINES_D_TAG));
        assert!(is_erv_publishable_d_tag(FITNESS_EQUIPMENT_D_TAG));
        assert!(is_erv_publishable_d_tag(TRAINING_PROFILE_D_TAG));
        assert!(is_erv_publishable_d_tag(MEDIA_LIBRARY_D_TAG));
        assert!(!is_erv_publishable_d_tag("erv/weight/2026-06-13"));
    }

    #[test]
    fn catalog_tags_are_readable_and_publishable() {
        assert!(is_erv_catalog_d_tag(WEIGHT_CATALOG_D_TAG));
        assert!(is_erv_d_tag(STRETCH_CATALOG_D_TAG));
        assert!(is_erv_publishable_d_tag(WEIGHT_CATALOG_D_TAG));
    }
}
