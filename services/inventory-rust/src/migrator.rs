use sea_orm_migration::prelude::*;

pub struct Migrator;

#[async_trait::async_trait]
impl MigratorTrait for Migrator {
    fn migrations() -> Vec<Box<dyn MigrationTrait>> {
        vec![
            Box::new(m20260526_000001_create_inventory_table::Migration),
            Box::new(m20260620_000001_create_reservations_table::Migration),
        ]
    }
}

pub mod m20260526_000001_create_inventory_table {
    use sea_orm_migration::prelude::*;

    #[derive(DeriveIden)]
    pub enum InventoryItems {
        Table,
        ProductId,
        QuantityAvailable,
    }

    #[derive(DeriveMigrationName)]
    pub struct Migration;

    #[async_trait::async_trait]
    impl MigrationTrait for Migration {
        async fn up(&self, manager: &SchemaManager) -> Result<(), DbErr> {
            manager
                .create_table(
                    Table::create()
                        .table(InventoryItems::Table)
                        .if_not_exists()
                        .col(
                            ColumnDef::new(InventoryItems::ProductId)
                                .string()
                                .not_null()
                                .primary_key(),
                        )
                        .col(ColumnDef::new(InventoryItems::QuantityAvailable).integer().not_null())
                        .to_owned(),
                )
                .await?;

            // Insert initial seed data
            let insert = sea_query::Query::insert()
                .into_table(InventoryItems::Table)
                .columns([
                    InventoryItems::ProductId,
                    InventoryItems::QuantityAvailable,
                ])
                .values_panic(["p1".into(), 100.into()])
                .values_panic(["p2".into(), 50.into()])
                .to_owned();

            manager.exec_stmt(insert).await?;

            Ok(())
        }

        async fn down(&self, manager: &SchemaManager) -> Result<(), DbErr> {
            manager
                .drop_table(Table::drop().table(InventoryItems::Table).to_owned())
                .await
        }
    }
}

pub mod m20260620_000001_create_reservations_table {
    use sea_orm_migration::prelude::*;

    #[derive(DeriveIden)]
    pub enum Reservations {
        Table,
        Id,
        OrderId,
        ProductId,
        Quantity,
        CreatedAt,
    }

    #[derive(DeriveMigrationName)]
    pub struct Migration;

    #[async_trait::async_trait]
    impl MigrationTrait for Migration {
        async fn up(&self, manager: &SchemaManager) -> Result<(), DbErr> {
            manager
                .create_table(
                    Table::create()
                        .table(Reservations::Table)
                        .if_not_exists()
                        .col(
                            ColumnDef::new(Reservations::Id)
                                .integer()
                                .not_null()
                                .auto_increment()
                                .primary_key(),
                        )
                        .col(ColumnDef::new(Reservations::OrderId).string().not_null())
                        .col(ColumnDef::new(Reservations::ProductId).string().not_null())
                        .col(ColumnDef::new(Reservations::Quantity).integer().not_null())
                        .col(ColumnDef::new(Reservations::CreatedAt).string().not_null())
                        .to_owned(),
                )
                .await?;

            // Unique constraint on (order_id, product_id) prevents
            // double-reservation when the orchestrator re-delivers an event.
            manager
                .create_index(
                    Index::create()
                        .table(Reservations::Table)
                        .name("idx_reservations_order_product_unique")
                        .col(Reservations::OrderId)
                        .col(Reservations::ProductId)
                        .unique()
                        .to_owned(),
                )
                .await?;

            Ok(())
        }

        async fn down(&self, manager: &SchemaManager) -> Result<(), DbErr> {
            manager
                .drop_table(Table::drop().table(Reservations::Table).to_owned())
                .await
        }
    }
}
