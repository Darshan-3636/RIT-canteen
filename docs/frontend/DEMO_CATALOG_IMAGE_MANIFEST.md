# Demo catalog image manifest

All 72 seeded Android item IDs have an explicit, one-to-one local resource mapping in `CatalogArtwork` / `foodPhotoResource`. The first 12 images are existing bundled concept assets; the 60 new images were created for this milestone with Codex built-in image generation, then center-cropped to 640×480 and encoded as WebP in `drawable-nodpi`. They are illustrative menu visuals, not photos of verified RIT food or sellers. No external images were scraped or hotlinked. Seller-selected local photo URIs take precedence in debug editing; future seller-created items without a bundled ID use the neutral fallback.

The prompt set used one item-specific subject prompt per new ID, with a neutral clean tabletop, realistic appetizing Indian campus-food/bakery/packaged-product photography, restrained warm natural light, 4:3 landscape close-up, and explicit exclusions for brands, text, watermarks, hands and prices. Pantry packaging was requested as generic and unbranded. Distinct subjects included recognizable preparation/ingredients (for example, set dosa as three soft dosas; rava idli as textured semolina cakes; paneer puff with visible filling; sparkling water with bubbles). The images remain demos and require merchant/content approval before real catalog use.

| Seller | Stable item ID | Image path | Provenance |
| --- | --- | --- | --- |
| `main-canteen` | `masala-dosa` | `app/src/main/res/drawable-nodpi/catalog_dosa.webp` | Existing bundled concept asset |
| `main-canteen` | `veg-biryani` | `app/src/main/res/drawable-nodpi/catalog_biryani.webp` | Existing bundled concept asset |
| `main-canteen` | `filter-coffee` | `app/src/main/res/drawable-nodpi/catalog_coffee.webp` | Existing bundled concept asset |
| `main-canteen` | `idli` | `app/src/main/res/drawable-nodpi/food_idli.webp` | Existing bundled concept asset |
| `main-canteen` | `lemon-rice` | `app/src/main/res/drawable-nodpi/food_lemon_rice.webp` | Existing bundled concept asset |
| `main-canteen` | `water-bottle` | `app/src/main/res/drawable-nodpi/food_water_bottle.webp` | Existing bundled concept asset |
| `main-canteen` | `plain-dosa` | `app/src/main/res/drawable-nodpi/demo_food_plain_dosa.webp` | New original generated demo image |
| `main-canteen` | `set-dosa` | `app/src/main/res/drawable-nodpi/demo_food_set_dosa.webp` | New original generated demo image |
| `main-canteen` | `rava-idli` | `app/src/main/res/drawable-nodpi/demo_food_rava_idli.webp` | New original generated demo image |
| `main-canteen` | `vada-sambar` | `app/src/main/res/drawable-nodpi/demo_food_vada_sambar.webp` | New original generated demo image |
| `main-canteen` | `ven-pongal` | `app/src/main/res/drawable-nodpi/demo_food_ven_pongal.webp` | New original generated demo image |
| `main-canteen` | `poori-sagu` | `app/src/main/res/drawable-nodpi/demo_food_poori_sagu.webp` | New original generated demo image |
| `main-canteen` | `veg-pulao` | `app/src/main/res/drawable-nodpi/demo_food_veg_pulao.webp` | New original generated demo image |
| `main-canteen` | `curd-rice` | `app/src/main/res/drawable-nodpi/demo_food_curd_rice.webp` | New original generated demo image |
| `main-canteen` | `bisi-bele-bath` | `app/src/main/res/drawable-nodpi/demo_food_bisi_bele_bath.webp` | New original generated demo image |
| `main-canteen` | `chapati-meal` | `app/src/main/res/drawable-nodpi/demo_food_chapati_meal.webp` | New original generated demo image |
| `main-canteen` | `veg-thali` | `app/src/main/res/drawable-nodpi/demo_food_veg_thali.webp` | New original generated demo image |
| `main-canteen` | `tomato-rice` | `app/src/main/res/drawable-nodpi/demo_food_tomato_rice.webp` | New original generated demo image |
| `north-counter` | `north-lunch` | `app/src/main/res/drawable-nodpi/food_north_lunch.webp` | Existing bundled concept asset |
| `north-counter` | `north-snack` | `app/src/main/res/drawable-nodpi/food_north_snack.webp` | Existing bundled concept asset |
| `north-counter` | `veg-samosa` | `app/src/main/res/drawable-nodpi/demo_food_veg_samosa.webp` | New original generated demo image |
| `north-counter` | `khasta-kachori` | `app/src/main/res/drawable-nodpi/demo_food_khasta_kachori.webp` | New original generated demo image |
| `north-counter` | `aloo-puff` | `app/src/main/res/drawable-nodpi/demo_food_aloo_puff.webp` | New original generated demo image |
| `north-counter` | `veg-cutlet` | `app/src/main/res/drawable-nodpi/demo_food_veg_cutlet.webp` | New original generated demo image |
| `north-counter` | `onion-pakora` | `app/src/main/res/drawable-nodpi/demo_food_onion_pakora.webp` | New original generated demo image |
| `north-counter` | `bhel-puri` | `app/src/main/res/drawable-nodpi/demo_food_bhel_puri.webp` | New original generated demo image |
| `north-counter` | `sev-puri` | `app/src/main/res/drawable-nodpi/demo_food_sev_puri.webp` | New original generated demo image |
| `north-counter` | `masala-puri` | `app/src/main/res/drawable-nodpi/demo_food_masala_puri.webp` | New original generated demo image |
| `north-counter` | `paneer-roll` | `app/src/main/res/drawable-nodpi/demo_food_paneer_roll.webp` | New original generated demo image |
| `north-counter` | `veg-sandwich` | `app/src/main/res/drawable-nodpi/demo_food_veg_sandwich.webp` | New original generated demo image |
| `north-counter` | `grilled-cheese-sandwich` | `app/src/main/res/drawable-nodpi/demo_food_grilled_cheese_sandwich.webp` | New original generated demo image |
| `north-counter` | `corn-chaat` | `app/src/main/res/drawable-nodpi/demo_food_corn_chaat.webp` | New original generated demo image |
| `north-counter` | `bread-pakora` | `app/src/main/res/drawable-nodpi/demo_food_bread_pakora.webp` | New original generated demo image |
| `north-counter` | `batata-vada` | `app/src/main/res/drawable-nodpi/demo_food_batata_vada.webp` | New original generated demo image |
| `north-counter` | `veg-momos` | `app/src/main/res/drawable-nodpi/demo_food_veg_momos.webp` | New original generated demo image |
| `north-counter` | `masala-fries` | `app/src/main/res/drawable-nodpi/demo_food_masala_fries.webp` | New original generated demo image |
| `coffee-corner` | `corner-coffee` | `app/src/main/res/drawable-nodpi/food_corner_coffee.webp` | Existing bundled concept asset |
| `coffee-corner` | `corner-biscuits` | `app/src/main/res/drawable-nodpi/food_corner_biscuits.webp` | Existing bundled concept asset |
| `coffee-corner` | `bakery-veg-puff` | `app/src/main/res/drawable-nodpi/demo_food_bakery_veg_puff.webp` | New original generated demo image |
| `coffee-corner` | `bakery-paneer-puff` | `app/src/main/res/drawable-nodpi/demo_food_bakery_paneer_puff.webp` | New original generated demo image |
| `coffee-corner` | `bakery-mushroom-puff` | `app/src/main/res/drawable-nodpi/demo_food_bakery_mushroom_puff.webp` | New original generated demo image |
| `coffee-corner` | `butter-croissant` | `app/src/main/res/drawable-nodpi/demo_food_butter_croissant.webp` | New original generated demo image |
| `coffee-corner` | `chocolate-croissant` | `app/src/main/res/drawable-nodpi/demo_food_chocolate_croissant.webp` | New original generated demo image |
| `coffee-corner` | `cinnamon-roll` | `app/src/main/res/drawable-nodpi/demo_food_cinnamon_roll.webp` | New original generated demo image |
| `coffee-corner` | `banana-bread` | `app/src/main/res/drawable-nodpi/demo_food_banana_bread.webp` | New original generated demo image |
| `coffee-corner` | `garlic-bun` | `app/src/main/res/drawable-nodpi/demo_food_garlic_bun.webp` | New original generated demo image |
| `coffee-corner` | `cheese-bun` | `app/src/main/res/drawable-nodpi/demo_food_cheese_bun.webp` | New original generated demo image |
| `coffee-corner` | `blueberry-muffin` | `app/src/main/res/drawable-nodpi/demo_food_blueberry_muffin.webp` | New original generated demo image |
| `coffee-corner` | `chocolate-muffin` | `app/src/main/res/drawable-nodpi/demo_food_chocolate_muffin.webp` | New original generated demo image |
| `coffee-corner` | `fudge-brownie` | `app/src/main/res/drawable-nodpi/demo_food_fudge_brownie.webp` | New original generated demo image |
| `coffee-corner` | `vanilla-pastry` | `app/src/main/res/drawable-nodpi/demo_food_vanilla_pastry.webp` | New original generated demo image |
| `coffee-corner` | `eggless-chocolate-cake` | `app/src/main/res/drawable-nodpi/demo_food_eggless_chocolate_cake.webp` | New original generated demo image |
| `coffee-corner` | `shortbread-cookies` | `app/src/main/res/drawable-nodpi/demo_food_shortbread_cookies.webp` | New original generated demo image |
| `coffee-corner` | `almond-cookies` | `app/src/main/res/drawable-nodpi/demo_food_almond_cookies.webp` | New original generated demo image |
| `campus-pantry` | `pantry-juice` | `app/src/main/res/drawable-nodpi/food_pantry_juice.webp` | Existing bundled concept asset |
| `campus-pantry` | `pantry-water` | `app/src/main/res/drawable-nodpi/food_pantry_water.webp` | Existing bundled concept asset |
| `campus-pantry` | `orange-juice-bottle` | `app/src/main/res/drawable-nodpi/demo_food_orange_juice_bottle.webp` | New original generated demo image |
| `campus-pantry` | `mango-juice-bottle` | `app/src/main/res/drawable-nodpi/demo_food_mango_juice_bottle.webp` | New original generated demo image |
| `campus-pantry` | `apple-juice-bottle` | `app/src/main/res/drawable-nodpi/demo_food_apple_juice_bottle.webp` | New original generated demo image |
| `campus-pantry` | `lemon-iced-tea` | `app/src/main/res/drawable-nodpi/demo_food_lemon_iced_tea.webp` | New original generated demo image |
| `campus-pantry` | `cold-coffee-bottle` | `app/src/main/res/drawable-nodpi/demo_food_cold_coffee_bottle.webp` | New original generated demo image |
| `campus-pantry` | `coconut-water-pack` | `app/src/main/res/drawable-nodpi/demo_food_coconut_water_pack.webp` | New original generated demo image |
| `campus-pantry` | `salted-lassi-pack` | `app/src/main/res/drawable-nodpi/demo_food_salted_lassi_pack.webp` | New original generated demo image |
| `campus-pantry` | `sparkling-water` | `app/src/main/res/drawable-nodpi/demo_food_sparkling_water.webp` | New original generated demo image |
| `campus-pantry` | `trail-mix` | `app/src/main/res/drawable-nodpi/demo_food_trail_mix.webp` | New original generated demo image |
| `campus-pantry` | `roasted-peanuts` | `app/src/main/res/drawable-nodpi/demo_food_roasted_peanuts.webp` | New original generated demo image |
| `campus-pantry` | `potato-chips` | `app/src/main/res/drawable-nodpi/demo_food_potato_chips.webp` | New original generated demo image |
| `campus-pantry` | `banana-chips` | `app/src/main/res/drawable-nodpi/demo_food_banana_chips.webp` | New original generated demo image |
| `campus-pantry` | `pantry-biscuit-pack` | `app/src/main/res/drawable-nodpi/demo_food_pantry_biscuit_pack.webp` | New original generated demo image |
| `campus-pantry` | `granola-bar` | `app/src/main/res/drawable-nodpi/demo_food_granola_bar.webp` | New original generated demo image |
| `campus-pantry` | `energy-bar` | `app/src/main/res/drawable-nodpi/demo_food_energy_bar.webp` | New original generated demo image |
| `campus-pantry` | `cracker-pack` | `app/src/main/res/drawable-nodpi/demo_food_cracker_pack.webp` | New original generated demo image |
