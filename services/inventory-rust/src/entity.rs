//! SeaORM entity for the `inventory_items` table.
//!
//! Each row represents one product's current stock level.
//! The `product_id` matches the `id` field in the catalog `products` table.

use sea_orm::entity::prelude::*;
use serde::{Deserialize, Serialize};

/// A single inventory record tracked in PostgreSQL.
#[derive(Clone, Debug, PartialEq, DeriveEntityModel, Serialize, Deserialize)]
#[sea_orm(table_name = "inventory_items")]
pub struct Model {
    #[sea_orm(primary_key, auto_increment = false)]
    pub product_id: String,
    pub quantity_available: i32,
}

#[derive(Copy, Clone, Debug, EnumIter, DeriveRelation)]
pub enum Relation {}

impl ActiveModelBehavior for ActiveModel {}
