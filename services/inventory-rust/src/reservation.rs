//! SeaORM entity for the `reservations` table.
//!
//! Each row represents one product reserved for one order.
//! The unique constraint on `(order_id, product_id)` provides
//! idempotency: duplicate reservation requests are silently skipped.

use sea_orm::entity::prelude::*;
use serde::{Deserialize, Serialize};

/// A reservation linking an order to a product with a quantity.
#[derive(Clone, Debug, PartialEq, DeriveEntityModel, Serialize, Deserialize)]
#[sea_orm(table_name = "reservations")]
pub struct Model {
    #[sea_orm(primary_key)]
    pub id: i32,
    pub order_id: String,
    pub product_id: String,
    pub quantity: i32,
    pub created_at: String,
}

#[derive(Copy, Clone, Debug, EnumIter, DeriveRelation)]
pub enum Relation {}

impl ActiveModelBehavior for ActiveModel {}
