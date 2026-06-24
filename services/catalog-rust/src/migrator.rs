use sea_orm_migration::prelude::*;

pub struct Migrator;

#[async_trait::async_trait]
impl MigratorTrait for Migrator {
    fn migrations() -> Vec<Box<dyn MigrationTrait>> {
        vec![Box::new(m20260526_000001_create_products_table::Migration)]
    }
}

pub mod m20260526_000001_create_products_table {
    use sea_orm_migration::prelude::*;

    #[derive(DeriveIden)]
    pub enum Products {
        Table,
        Id,
        Name,
        Description,
        Category,
        ImageUrl,
        Price,
        Stock,
        InStock,
    }

    #[derive(DeriveMigrationName)]
    pub struct Migration;

    #[async_trait::async_trait]
    impl MigrationTrait for Migration {
        async fn up(&self, manager: &SchemaManager) -> Result<(), DbErr> {
            manager
                .create_table(
                    Table::create()
                        .table(Products::Table)
                        .if_not_exists()
                        .col(
                            ColumnDef::new(Products::Id)
                                .string()
                                .not_null()
                                .primary_key(),
                        )
                        .col(ColumnDef::new(Products::Name).string().not_null())
                        .col(ColumnDef::new(Products::Description).text().not_null())
                        .col(ColumnDef::new(Products::Category).string().not_null())
                        .col(ColumnDef::new(Products::ImageUrl).text().not_null())
                        .col(ColumnDef::new(Products::Price).double().not_null())
                        .col(ColumnDef::new(Products::Stock).integer().not_null())
                        .col(ColumnDef::new(Products::InStock).boolean().not_null())
                        .to_owned(),
                )
                .await?;

            // Insert initial seed data
            let insert = sea_query::Query::insert()
                .into_table(Products::Table)
                .columns([
                    Products::Id,
                    Products::Name,
                    Products::Description,
                    Products::Category,
                    Products::ImageUrl,
                    Products::Price,
                    Products::Stock,
                    Products::InStock,
                ])
                .values_panic([
                    "p1".into(),
                    "Monstera Deliciosa".into(),
                    "A lush statement plant with dramatic split leaves for indoors.".into(),
                    "Indoor Plants".into(),
                    "https://images.unsplash.com/photo-1519710164239-da123dc03ef4?auto=format&fit=crop&w=800&q=80".into(),
                    34.99.into(),
                    24.into(),
                    true.into(),
                ])
                .values_panic([
                    "p2".into(),
                    "Fiddle Leaf Fig".into(),
                    "A top-trending indoor tree for bright rooms and modern design.".into(),
                    "Indoor Plants".into(),
                    "https://images.unsplash.com/photo-1501004318641-b39e6451bec6?auto=format&fit=crop&w=800&q=80".into(),
                    49.99.into(),
                    0.into(),
                    false.into(),
                ])
                .values_panic([
                    "p3".into(),
                    "Succulent Trio".into(),
                    "A hardy collection of succulents in a minimal planter set.".into(),
                    "Outdoor Plants".into(),
                    "https://images.unsplash.com/photo-1512436991641-6745cdb1723f?auto=format&fit=crop&w=800&q=80".into(),
                    22.0.into(),
                    18.into(),
                    true.into(),
                ])
                .values_panic([
                    "p4".into(),
                    "Ceramic Planter".into(),
                    "A handcrafted planter designed for stylish home displays.".into(),
                    "Planters & Care".into(),
                    "https://images.unsplash.com/photo-1492447166138-50c3889fccb1?auto=format&fit=crop&w=800&q=80".into(),
                    19.5.into(),
                    48.into(),
                    true.into(),
                ])
                .values_panic([
                    "p5".into(),
                    "Plant Care Kit".into(),
                    "Everything you need to prune, feed, and nurture your indoor plants.".into(),
                    "Planters & Care".into(),
                    "https://images.unsplash.com/photo-1501004318641-b39e6451bec6?auto=format&fit=crop&w=800&q=80".into(),
                    24.99.into(),
                    30.into(),
                    true.into(),
                ])
                .values_panic([
                    "p6".into(),
                    "Garden Scissors".into(),
                    "Precision shears for trimming delicate leaves and stems.".into(),
                    "Accessories".into(),
                    "https://images.unsplash.com/photo-1511866088241-7ea6db70f720?auto=format&fit=crop&w=800&q=80".into(),
                    12.99.into(),
                    72.into(),
                    true.into(),
                ])
                .to_owned();

            manager.exec_stmt(insert).await?;

            Ok(())
        }

        async fn down(&self, manager: &SchemaManager) -> Result<(), DbErr> {
            manager
                .drop_table(Table::drop().table(Products::Table).to_owned())
                .await
        }
    }
}
