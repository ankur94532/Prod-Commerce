package com.gocommerce.catalog.seed;

import com.gocommerce.catalog.entity.Product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProductSeedCatalog {

    private ProductSeedCatalog() {
    }

    public static List<Product> products() {
        return expandToTarget(baseProducts(), 1_000);
    }

    public static List<Product> products(int targetCount) {
        if (targetCount < 1) {
            throw new IllegalArgumentException("targetCount must be positive");
        }
        return expandToTarget(baseProducts(), targetCount);
    }

    private static List<Product> baseProducts() {
        return List.of(
                p("s26-ultra-256gb-gray", "Galaxy S26 Ultra 256GB (Gray)", "Flagship smartphone with high-end camera and display.", "89999", "smartphones", "Samsung", 50, Map.of("storage", "256GB", "color", "Gray")),
                p("s26-ultra-512gb-black", "Galaxy S26 Ultra 512GB (Black)", "Flagship smartphone with extra storage for power users.", "99999", "smartphones", "Samsung", 30, Map.of("storage", "512GB", "color", "Black")),
                p("pixel-10-pro-256gb-porcelain", "Pixel 10 Pro 256GB (Porcelain)", "AI-ready Android phone with pro-grade photos and all-day battery.", "84999", "smartphones", "Google", 45, Map.of("storage", "256GB", "color", "Porcelain")),
                p("oneplus-14-5g-256gb-green", "OnePlus 14 5G 256GB (Green)", "Fast-charging performance phone with smooth AMOLED display.", "64999", "smartphones", "OnePlus", 60, Map.of("storage", "256GB", "color", "Green")),
                p("iphone-17-128gb-blue", "iPhone 17 128GB (Blue)", "Premium smartphone with bright display and advanced camera system.", "79999", "smartphones", "Apple", 35, Map.of("storage", "128GB", "color", "Blue")),
                p("moto-edge-70-128gb-black", "Moto Edge 70 128GB (Black)", "Slim 5G phone with clean software and rapid charging.", "32999", "smartphones", "Motorola", 75, Map.of("storage", "128GB", "color", "Black")),
                p("redmi-note-16-pro-256gb-purple", "Redmi Note 16 Pro 256GB (Purple)", "High-value 5G smartphone with large battery and crisp display.", "23999", "smartphones", "Redmi", 90, Map.of("storage", "256GB", "color", "Purple")),

                p("buds-pro-2", "Galaxy Buds Pro 2", "Wireless earbuds with ANC and long battery life.", "14999", "earbuds-headphones", "Samsung", 100, Map.of("color", "White", "type", "earbuds")),
                p("airpods-pro-3-usb-c", "AirPods Pro 3 USB-C", "Compact earbuds with adaptive noise cancellation and spatial audio.", "24999", "earbuds-headphones", "Apple", 42, Map.of("color", "White", "type", "earbuds")),
                p("sony-wh-1000xm6-black", "Sony WH-1000XM6 Headphones", "Wireless over-ear headphones with flagship noise cancellation.", "34999", "earbuds-headphones", "Sony", 28, Map.of("color", "Black", "type", "over-ear")),
                p("jbl-tune-770nc-blue", "JBL Tune 770NC Headphones", "Lightweight wireless headphones with punchy bass and ANC.", "7999", "earbuds-headphones", "JBL", 80, Map.of("color", "Blue", "type", "over-ear")),
                p("boat-airdopes-311-pro", "boAt Airdopes 311 Pro", "Everyday true wireless earbuds with quick pairing.", "1999", "earbuds-headphones", "boAt", 140, Map.of("color", "Black", "type", "earbuds")),
                p("nothing-ear-4-transparent", "Nothing Ear 4", "Transparent design earbuds with clear voice calls and ANC.", "11999", "earbuds-headphones", "Nothing", 55, Map.of("color", "Transparent", "type", "earbuds")),
                p("soundcore-life-q35", "Soundcore Life Q35 Headphones", "Travel headphones with hybrid ANC and soft ear cushions.", "9999", "earbuds-headphones", "Soundcore", 63, Map.of("color", "Navy", "type", "over-ear")),

                p("macbook-air-13-m4-256gb", "MacBook Air 13 M4 256GB", "Thin and light laptop for students, creators, and everyday work.", "114900", "laptops", "Apple", 24, Map.of("memory", "16GB", "storage", "256GB")),
                p("dell-xps-14-ultrabook", "Dell XPS 14 Ultrabook", "Premium Windows laptop with compact build and sharp display.", "149990", "laptops", "Dell", 18, Map.of("memory", "16GB", "storage", "512GB")),
                p("hp-victus-16-gaming-laptop", "HP Victus 16 Gaming Laptop", "Gaming laptop with dedicated graphics and high-refresh display.", "87999", "laptops", "HP", 22, Map.of("memory", "16GB", "storage", "1TB")),
                p("lenovo-ideapad-slim-5", "Lenovo IdeaPad Slim 5", "Reliable everyday laptop with metal body and long battery life.", "62999", "laptops", "Lenovo", 34, Map.of("memory", "16GB", "storage", "512GB")),
                p("asus-zenbook-oled-14", "ASUS Zenbook OLED 14", "Portable laptop with vivid OLED display and fast SSD.", "92999", "laptops", "ASUS", 20, Map.of("memory", "16GB", "storage", "1TB")),
                p("acer-aspire-lite-15", "Acer Aspire Lite 15", "Affordable laptop for browsing, office work, and online classes.", "38999", "laptops", "Acer", 48, Map.of("memory", "8GB", "storage", "512GB")),
                p("msi-thin-gf63-gaming", "MSI Thin GF63 Gaming Laptop", "Entry gaming laptop with slim chassis and backlit keyboard.", "67999", "laptops", "MSI", 26, Map.of("memory", "16GB", "storage", "512GB")),

                p("ipad-air-11-128gb", "iPad Air 11 128GB", "Light tablet for notes, streaming, drawing, and work.", "59900", "tablets", "Apple", 32, Map.of("storage", "128GB", "screen", "11 inch")),
                p("galaxy-tab-s10-128gb", "Galaxy Tab S10 128GB", "Android tablet with AMOLED display and included stylus.", "54999", "tablets", "Samsung", 38, Map.of("storage", "128GB", "screen", "11 inch")),
                p("oneplus-pad-2", "OnePlus Pad 2", "Fast tablet for entertainment, multitasking, and reading.", "39999", "tablets", "OnePlus", 44, Map.of("storage", "256GB", "screen", "12 inch")),
                p("lenovo-tab-m11", "Lenovo Tab M11", "Family tablet with large display and quad speakers.", "18999", "tablets", "Lenovo", 58, Map.of("storage", "128GB", "screen", "11 inch")),
                p("kindle-paperwhite-16gb", "Kindle Paperwhite 16GB", "Water-resistant e-reader with glare-free display.", "14999", "tablets", "Amazon", 70, Map.of("storage", "16GB", "type", "e-reader")),

                p("apple-watch-series-12", "Apple Watch Series 12", "Smartwatch for fitness, notifications, and health tracking.", "45900", "watches-wearables", "Apple", 30, Map.of("color", "Midnight", "size", "45mm")),
                p("galaxy-watch-8-classic", "Galaxy Watch 8 Classic", "Android smartwatch with rotating bezel and sleep tracking.", "34999", "watches-wearables", "Samsung", 36, Map.of("color", "Black", "size", "46mm")),
                p("fitbit-charge-7", "Fitbit Charge 7", "Slim fitness band with heart-rate and activity tracking.", "12999", "watches-wearables", "Fitbit", 64, Map.of("color", "Graphite", "type", "fitness band")),
                p("garmin-forerunner-265", "Garmin Forerunner 265", "GPS running watch with training metrics and AMOLED display.", "42999", "watches-wearables", "Garmin", 18, Map.of("color", "Aqua", "type", "sports watch")),
                p("amazfit-gtr-5", "Amazfit GTR 5", "Round smartwatch with long battery life and workout modes.", "17999", "watches-wearables", "Amazfit", 52, Map.of("color", "Brown", "type", "smartwatch")),

                p("linen-cotton-shirt-sky-blue", "Men Linen Cotton Shirt", "Breathable casual shirt for warm-weather everyday wear.", "1699", "mens-shirts", "Symbol", 120, Map.of("color", "Sky Blue", "fit", "Regular")),
                p("oxford-formal-shirt-white", "Men Oxford Formal Shirt", "Classic white office shirt with crisp collar and button cuffs.", "1899", "mens-shirts", "Van Heusen", 95, Map.of("color", "White", "fit", "Slim")),
                p("checked-flannel-shirt-red", "Men Checked Flannel Shirt", "Soft brushed flannel shirt for casual layering.", "1499", "mens-shirts", "Highlander", 110, Map.of("color", "Red", "fit", "Regular")),
                p("denim-shirt-dark-wash", "Men Denim Shirt Dark Wash", "Rugged denim shirt with chest pockets.", "2199", "mens-shirts", "Levi's", 70, Map.of("color", "Dark Blue", "fit", "Regular")),
                p("printed-vacation-shirt-green", "Men Printed Vacation Shirt", "Relaxed resort shirt with tropical print.", "1299", "mens-shirts", "Dennis Lingo", 135, Map.of("color", "Green", "fit", "Relaxed")),
                p("mandarin-collar-shirt-black", "Men Mandarin Collar Shirt", "Minimal smart-casual shirt with clean collar design.", "1599", "mens-shirts", "Peter England", 88, Map.of("color", "Black", "fit", "Slim")),
                p("striped-cotton-shirt-navy", "Men Striped Cotton Shirt", "Light cotton shirt with vertical stripes.", "1399", "mens-shirts", "Arrow", 102, Map.of("color", "Navy", "fit", "Regular")),

                p("solid-crew-neck-tshirt-black", "Men Solid Crew Neck T-Shirt", "Soft cotton everyday T-shirt with classic crew neck.", "599", "mens-tshirts", "Amazon Essentials", 220, Map.of("color", "Black", "fit", "Regular")),
                p("graphic-oversized-tshirt-white", "Men Graphic Oversized T-Shirt", "Relaxed streetwear T-shirt with front graphic.", "899", "mens-tshirts", "Bewakoof", 180, Map.of("color", "White", "fit", "Oversized")),
                p("polo-tshirt-maroon", "Men Polo T-Shirt", "Smart casual polo with ribbed collar.", "1199", "mens-tshirts", "U.S. Polo Assn.", 125, Map.of("color", "Maroon", "fit", "Regular")),
                p("dry-fit-training-tshirt-blue", "Men Dry-Fit Training T-Shirt", "Moisture-wicking tee for gym and running.", "799", "mens-tshirts", "HRX", 160, Map.of("color", "Blue", "material", "Polyester")),
                p("henley-tshirt-olive", "Men Henley T-Shirt", "Casual Henley tee with three-button placket.", "999", "mens-tshirts", "Roadster", 118, Map.of("color", "Olive", "fit", "Regular")),
                p("longline-tshirt-charcoal", "Men Longline T-Shirt", "Extended length T-shirt with minimal styling.", "749", "mens-tshirts", "The Souled Store", 130, Map.of("color", "Charcoal", "fit", "Longline")),

                p("slim-fit-jeans-dark-blue", "Men Slim Fit Jeans", "Stretch denim jeans with slim modern fit.", "2499", "mens-jeans-trousers", "Levi's", 85, Map.of("color", "Dark Blue", "fit", "Slim")),
                p("regular-fit-jeans-light-blue", "Men Regular Fit Jeans", "Everyday denim jeans with comfortable straight leg.", "1999", "mens-jeans-trousers", "Wrangler", 92, Map.of("color", "Light Blue", "fit", "Regular")),
                p("chino-trousers-khaki", "Men Chino Trousers", "Versatile cotton chinos for work or weekends.", "1799", "mens-jeans-trousers", "Allen Solly", 115, Map.of("color", "Khaki", "fit", "Slim")),
                p("formal-trousers-charcoal", "Men Formal Trousers", "Office trousers with flat front and clean drape.", "2299", "mens-jeans-trousers", "Van Heusen", 76, Map.of("color", "Charcoal", "fit", "Regular")),
                p("cargo-pants-olive", "Men Cargo Pants", "Utility pants with multiple pockets and relaxed fit.", "1899", "mens-jeans-trousers", "Highlander", 104, Map.of("color", "Olive", "fit", "Relaxed")),
                p("jogger-pants-black", "Men Jogger Pants", "Comfortable joggers with elastic waist and cuffs.", "1299", "mens-jeans-trousers", "HRX", 140, Map.of("color", "Black", "fit", "Tapered")),
                p("linen-trousers-beige", "Men Linen Blend Trousers", "Lightweight trousers for summer and travel.", "2099", "mens-jeans-trousers", "Marks & Spencer", 66, Map.of("color", "Beige", "fit", "Regular")),

                p("women-ribbed-top-lavender", "Women Ribbed Top", "Soft ribbed top for casual outfits.", "799", "womens-tops", "Tokyo Talkies", 150, Map.of("color", "Lavender", "fit", "Slim")),
                p("women-cotton-kurti-teal", "Women Cotton Kurti", "Printed cotton kurti for daily wear.", "1199", "womens-tops", "Aurelia", 130, Map.of("color", "Teal", "material", "Cotton")),
                p("women-satin-blouse-cream", "Women Satin Blouse", "Elegant blouse with soft drape and subtle sheen.", "1599", "womens-tops", "ONLY", 78, Map.of("color", "Cream", "fit", "Regular")),
                p("women-oversized-shirt-pink", "Women Oversized Shirt", "Relaxed button-down shirt for layered styling.", "1399", "womens-tops", "H&M", 95, Map.of("color", "Pink", "fit", "Oversized")),
                p("women-peplum-top-black", "Women Peplum Top", "Dressy peplum top with flattering waistline.", "1299", "womens-tops", "Vero Moda", 84, Map.of("color", "Black", "fit", "Regular")),
                p("women-athleisure-tank-grey", "Women Athleisure Tank Top", "Lightweight training tank with breathable fabric.", "699", "womens-tops", "Puma", 135, Map.of("color", "Grey", "material", "Polyester")),

                p("women-floral-midi-dress", "Women Floral Midi Dress", "Flowy floral dress for brunch and casual occasions.", "1999", "womens-dresses", "Harpa", 82, Map.of("color", "Yellow", "length", "Midi")),
                p("women-a-line-dress-navy", "Women A-Line Dress", "Classic A-line dress with clean silhouette.", "2499", "womens-dresses", "Van Heusen", 54, Map.of("color", "Navy", "length", "Knee")),
                p("women-wrap-dress-rust", "Women Wrap Dress", "Comfortable wrap dress with adjustable tie waist.", "1799", "womens-dresses", "AND", 68, Map.of("color", "Rust", "length", "Midi")),
                p("women-shirt-dress-denim", "Women Denim Shirt Dress", "Casual shirt dress in soft denim fabric.", "2299", "womens-dresses", "Roadster", 61, Map.of("color", "Blue", "material", "Denim")),
                p("women-evening-dress-black", "Women Evening Dress", "Minimal black dress for dinners and evening events.", "2999", "womens-dresses", "Mango", 40, Map.of("color", "Black", "length", "Midi")),

                p("running-shoes-men-black", "Men Running Shoes", "Cushioned running shoes for daily training.", "3499", "footwear", "Nike", 90, Map.of("color", "Black", "type", "Running")),
                p("women-walking-shoes-pink", "Women Walking Shoes", "Lightweight walking shoes with breathable upper.", "2799", "footwear", "Skechers", 85, Map.of("color", "Pink", "type", "Walking")),
                p("leather-formal-shoes-brown", "Men Leather Formal Shoes", "Polished derby shoes for office and events.", "3999", "footwear", "Red Tape", 58, Map.of("color", "Brown", "type", "Formal")),
                p("canvas-sneakers-white", "Unisex Canvas Sneakers", "Classic low-top canvas sneakers for everyday wear.", "1499", "footwear", "Converse", 120, Map.of("color", "White", "type", "Sneakers")),
                p("sports-sandals-grey", "Sports Sandals", "Comfortable sandals with adjustable straps.", "1299", "footwear", "Woodland", 100, Map.of("color", "Grey", "type", "Sandals")),
                p("women-block-heels-nude", "Women Block Heels", "Stable block heels for formal and party outfits.", "2199", "footwear", "Catwalk", 72, Map.of("color", "Nude", "type", "Heels")),
                p("flip-flops-navy", "Unisex Flip Flops", "Soft everyday flip flops for home and travel.", "499", "footwear", "Crocs", 180, Map.of("color", "Navy", "type", "Flip Flops")),

                p("laptop-backpack-25l-black", "Laptop Backpack 25L", "Water-resistant backpack with padded laptop sleeve.", "2199", "bags-wallets", "American Tourister", 95, Map.of("color", "Black", "capacity", "25L")),
                p("women-tote-bag-tan", "Women Tote Bag", "Spacious tote bag for office and daily errands.", "1899", "bags-wallets", "Lavie", 76, Map.of("color", "Tan", "type", "Tote")),
                p("men-leather-wallet-brown", "Men Leather Wallet", "Compact bifold wallet with card slots.", "999", "bags-wallets", "WildHorn", 140, Map.of("color", "Brown", "material", "Leather")),
                p("travel-duffel-bag-45l", "Travel Duffel Bag 45L", "Weekend duffel with shoe compartment and shoulder strap.", "2499", "bags-wallets", "Skybags", 64, Map.of("color", "Grey", "capacity", "45L")),
                p("sling-bag-crossbody-black", "Crossbody Sling Bag", "Compact sling bag for phone, wallet, and keys.", "899", "bags-wallets", "Gear", 110, Map.of("color", "Black", "type", "Sling")),

                p("stainless-steel-water-bottle-1l", "Stainless Steel Water Bottle 1L", "Insulated bottle that keeps drinks hot or cold.", "899", "kitchen-dining", "Milton", 160, Map.of("capacity", "1L", "material", "Steel")),
                p("nonstick-fry-pan-28cm", "Non-Stick Fry Pan 28cm", "Durable fry pan with even heat distribution.", "1299", "kitchen-dining", "Prestige", 115, Map.of("size", "28cm", "material", "Aluminium")),
                p("dinner-set-24-piece-white", "24-Piece Dinner Set", "Microwave-safe dinnerware set for family dining.", "2499", "kitchen-dining", "Cello", 70, Map.of("pieces", "24", "color", "White")),
                p("glass-storage-jars-set-6", "Glass Storage Jars Set of 6", "Airtight jars for pantry organization.", "999", "kitchen-dining", "Borosil", 125, Map.of("pieces", "6", "material", "Glass")),
                p("electric-kettle-1-8l", "Electric Kettle 1.8L", "Quick-boil kettle with auto shut-off.", "1199", "kitchen-dining", "Philips", 105, Map.of("capacity", "1.8L", "power", "1500W")),

                p("air-fryer-4l-digital", "Digital Air Fryer 4L", "Oil-free cooking appliance with preset modes.", "5999", "home-appliances", "Instant", 55, Map.of("capacity", "4L", "power", "1400W")),
                p("robot-vacuum-cleaner", "Robot Vacuum Cleaner", "Smart vacuum for scheduled floor cleaning.", "17999", "home-appliances", "Eureka Forbes", 28, Map.of("type", "Robot", "runtime", "120 min")),
                p("tower-fan-remote-control", "Tower Fan with Remote", "Slim tower fan with oscillation and timer.", "6999", "home-appliances", "Orient", 44, Map.of("color", "White", "speed", "3 levels")),
                p("steam-iron-2200w", "Steam Iron 2200W", "Fast-heating steam iron with ceramic soleplate.", "1999", "home-appliances", "Bajaj", 86, Map.of("power", "2200W", "color", "Blue")),

                p("vitamin-c-face-serum", "Vitamin C Face Serum", "Brightening serum for daily skincare routine.", "699", "beauty-grooming", "Minimalist", 150, Map.of("volume", "30ml", "skinType", "All")),
                p("charcoal-face-wash", "Charcoal Face Wash", "Deep-cleansing face wash for oily skin.", "349", "beauty-grooming", "Garnier", 200, Map.of("volume", "100ml", "skinType", "Oily")),
                p("beard-trimmer-cordless", "Cordless Beard Trimmer", "Rechargeable trimmer with adjustable length settings.", "1499", "beauty-grooming", "Philips", 95, Map.of("runtime", "90 min", "type", "Cordless")),
                p("matte-lipstick-set-5", "Matte Lipstick Set of 5", "Long-wear lip colors in everyday shades.", "999", "beauty-grooming", "Maybelline", 120, Map.of("pieces", "5", "finish", "Matte")),

                p("yoga-mat-6mm-purple", "Yoga Mat 6mm", "Non-slip exercise mat for yoga and stretching.", "799", "fitness-sports", "Boldfit", 140, Map.of("thickness", "6mm", "color", "Purple")),
                p("adjustable-dumbbell-set-20kg", "Adjustable Dumbbell Set 20kg", "Space-saving dumbbell set for home workouts.", "3999", "fitness-sports", "Kore", 42, Map.of("weight", "20kg", "type", "Adjustable")),
                p("badminton-racket-set", "Badminton Racket Set", "Two-racket set with shuttlecocks and cover.", "1299", "fitness-sports", "Yonex", 75, Map.of("pieces", "2", "sport", "Badminton")),
                p("football-size-5", "Football Size 5", "Durable stitched football for training and matches.", "999", "fitness-sports", "Nivia", 110, Map.of("size", "5", "sport", "Football")),

                p("atomic-habits-paperback", "Atomic Habits Paperback", "Practical book on building better habits.", "499", "books-stationery", "Penguin", 180, Map.of("format", "Paperback", "language", "English")),
                p("ruled-notebooks-pack-6", "Ruled Notebooks Pack of 6", "Everyday notebooks for school, work, and journaling.", "399", "books-stationery", "Classmate", 220, Map.of("pages", "172", "pieces", "6")),
                p("gel-pens-set-10-blue", "Gel Pens Set of 10", "Smooth-writing blue gel pens for office and study.", "199", "books-stationery", "Pentonic", 260, Map.of("ink", "Blue", "pieces", "10")),
                p("desk-organizer-wooden", "Wooden Desk Organizer", "Compact organizer for pens, notes, and accessories.", "699", "books-stationery", "Solimo", 100, Map.of("material", "Wood", "color", "Natural")),

                p("usb-c-fast-charging-cable-1m", "USB-C Fast Charging Cable 1m", "Braided charging cable for phones and tablets.", "299", "accessories-cables", "Amazon Basics", 300, Map.of("length", "1m", "type", "USB-C")),
                p("65w-gan-fast-charger", "65W GaN Fast Charger", "Compact wall charger for phones, tablets, and laptops.", "2499", "accessories-cables", "Stuffcool", 90, Map.of("power", "65W", "ports", "3")),
                p("wireless-mouse-silent-click", "Wireless Mouse Silent Click", "Compact wireless mouse with quiet buttons.", "799", "accessories-cables", "Logitech", 160, Map.of("color", "Black", "connectivity", "2.4GHz")),
                p("mechanical-keyboard-rgb", "RGB Mechanical Keyboard", "Wired mechanical keyboard with tactile switches.", "3499", "accessories-cables", "Redragon", 70, Map.of("switch", "Brown", "layout", "TKL")),
                p("phone-stand-aluminium", "Aluminium Phone Stand", "Adjustable desk stand for phones and small tablets.", "499", "accessories-cables", "Portronics", 150, Map.of("material", "Aluminium", "color", "Silver"))
        );
    }

    private static List<Product> expandToTarget(List<Product> baseProducts, int targetCount) {
        if (targetCount <= baseProducts.size()) {
            return List.copyOf(baseProducts.subList(0, targetCount));
        }
        List<Product> products = new ArrayList<>(baseProducts);
        int variantNumber = 1;
        while (products.size() < targetCount) {
            for (Product baseProduct : baseProducts) {
                if (products.size() >= targetCount) {
                    break;
                }
                products.add(variant(baseProduct, variantNumber));
            }
            variantNumber++;
        }
        return List.copyOf(products);
    }

    private static Product variant(Product baseProduct, int variantNumber) {
        String descriptor = descriptorFor(baseProduct.getCategorySlug(), variantNumber);
        Map<String, String> attributes = variantAttributes(baseProduct, descriptor, variantNumber);
        String name = baseProduct.getName() + " " + descriptor;
        String slug = slugify(baseProduct.getSlug() + "-" + descriptor + "-" + variantNumber);
        BigDecimal price = baseProduct.getPrice().add(priceDelta(baseProduct, variantNumber));
        int stock = Math.max(5, baseProduct.getStockQuantity() + ((variantNumber % 7) * 9) - 18);

        return new Product(
                slug,
                name,
                descriptor + " variant. " + baseProduct.getDescription(),
                price,
                baseProduct.getCurrency(),
                baseProduct.getCategorySlug(),
                baseProduct.getBrand(),
                List.of(imageUrl(baseProduct.getCategorySlug(), name)),
                stock,
                true,
                attributes);
    }

    private static Map<String, String> variantAttributes(Product baseProduct, String descriptor, int variantNumber) {
        Map<String, String> attributes = new LinkedHashMap<>(baseProduct.getAttributes());
        String category = baseProduct.getCategorySlug();

        switch (category) {
            case "smartphones" -> {
                attributes.put("color", pick(List.of("Black", "Blue", "Green", "Silver", "Purple", "Gold", "White", "Graphite", "Red"), variantNumber));
                attributes.put("storage", pick(List.of("128GB", "256GB", "512GB", "1TB"), variantNumber));
            }
            case "laptops" -> {
                attributes.put("memory", pick(List.of("8GB", "16GB", "24GB", "32GB"), variantNumber));
                attributes.put("storage", pick(List.of("256GB", "512GB", "1TB", "2TB"), variantNumber + 1));
            }
            case "tablets" -> {
                attributes.put("storage", pick(List.of("64GB", "128GB", "256GB", "512GB"), variantNumber));
                attributes.put("screen", pick(List.of("10 inch", "11 inch", "12 inch", "13 inch"), variantNumber + 1));
            }
            case "earbuds-headphones" -> {
                attributes.put("color", pick(List.of("Black", "White", "Blue", "Navy", "Silver", "Beige"), variantNumber));
                attributes.put("type", pick(List.of("earbuds", "over-ear", "on-ear", "neckband"), variantNumber + 1));
            }
            case "watches-wearables" -> {
                attributes.put("color", pick(List.of("Black", "Silver", "Midnight", "Graphite", "Rose Gold", "Aqua"), variantNumber));
                attributes.put("size", pick(List.of("40mm", "42mm", "44mm", "45mm", "46mm"), variantNumber + 1));
            }
            case "mens-shirts", "mens-tshirts", "mens-jeans-trousers", "womens-tops" -> {
                attributes.put("color", pick(List.of("Black", "White", "Blue", "Navy", "Green", "Maroon", "Grey", "Beige", "Olive"), variantNumber));
                attributes.put("fit", pick(List.of("Regular", "Slim", "Relaxed", "Oversized", "Tapered"), variantNumber + 1));
            }
            case "womens-dresses" -> {
                attributes.put("color", pick(List.of("Black", "Navy", "Rust", "Yellow", "Pink", "Teal", "Lavender"), variantNumber));
                attributes.put("length", pick(List.of("Mini", "Knee", "Midi", "Maxi"), variantNumber + 1));
            }
            case "footwear" -> {
                attributes.put("color", pick(List.of("Black", "White", "Brown", "Grey", "Navy", "Pink", "Tan"), variantNumber));
                attributes.put("type", pick(List.of("Running", "Walking", "Formal", "Sneakers", "Sandals", "Heels"), variantNumber + 1));
            }
            case "bags-wallets" -> {
                attributes.put("color", pick(List.of("Black", "Brown", "Tan", "Grey", "Navy", "Olive"), variantNumber));
                attributes.put("material", pick(List.of("Polyester", "Leather", "Canvas", "Nylon"), variantNumber + 1));
            }
            case "kitchen-dining" -> {
                attributes.put("material", pick(List.of("Steel", "Glass", "Aluminium", "Ceramic", "Plastic"), variantNumber));
                attributes.put("capacity", pick(List.of("500ml", "1L", "1.5L", "2L", "4L"), variantNumber + 1));
            }
            case "home-appliances" -> {
                attributes.put("color", pick(List.of("White", "Black", "Blue", "Silver", "Grey"), variantNumber));
                attributes.put("power", pick(List.of("800W", "1200W", "1400W", "1500W", "2200W"), variantNumber + 1));
            }
            case "beauty-grooming" -> {
                attributes.put("volume", pick(List.of("30ml", "50ml", "100ml", "150ml", "200ml"), variantNumber));
                attributes.put("skinType", pick(List.of("All", "Oily", "Dry", "Sensitive"), variantNumber + 1));
            }
            case "fitness-sports" -> {
                attributes.put("sport", pick(List.of("Yoga", "Training", "Badminton", "Football", "Cricket"), variantNumber));
                attributes.put("type", pick(List.of("Beginner", "Adjustable", "Pro", "Training"), variantNumber + 1));
            }
            case "books-stationery" -> {
                attributes.put("format", pick(List.of("Paperback", "Hardcover", "Notebook", "Planner", "Set"), variantNumber));
                attributes.put("pieces", pick(List.of("1", "2", "5", "6", "10"), variantNumber + 1));
            }
            case "accessories-cables" -> {
                attributes.put("color", pick(List.of("Black", "White", "Silver", "Blue", "Grey"), variantNumber));
                attributes.put("type", pick(List.of("USB-C", "Wireless", "Wired", "Stand", "Adapter"), variantNumber + 1));
            }
            default -> attributes.put("variant", descriptor);
        }
        attributes.put("edition", descriptor);
        return attributes;
    }

    private static BigDecimal priceDelta(Product baseProduct, int variantNumber) {
        BigDecimal base = baseProduct.getPrice();
        BigDecimal percent = BigDecimal.valueOf((variantNumber % 9) - 4).movePointLeft(2);
        BigDecimal delta = base.multiply(percent);
        return delta.add(BigDecimal.valueOf((long) variantNumber * 17L));
    }

    private static String descriptorFor(String category, int variantNumber) {
        List<String> descriptors = switch (category) {
            case "smartphones" -> List.of("Midnight", "Aurora", "Titanium", "Cobalt", "Pearl", "Graphite", "Sunrise", "Forest", "Ruby");
            case "laptops" -> List.of("Creator", "Business", "Student", "Gaming", "Pro", "Air", "Studio", "Compact", "Elite");
            case "tablets" -> List.of("WiFi", "Cellular", "Kids", "Sketch", "Reader", "Plus", "Mini", "Studio", "Pro");
            case "earbuds-headphones" -> List.of("Bass", "Travel", "Studio", "Sport", "Clear", "Comfort", "Max", "Lite", "Pro");
            case "watches-wearables" -> List.of("Active", "Classic", "Trail", "Metro", "Sport", "Wellness", "Steel", "Lite", "Pro");
            case "mens-shirts", "mens-tshirts", "mens-jeans-trousers" -> List.of("Classic", "Urban", "Weekend", "Office", "Travel", "Premium", "Essential", "Comfort", "Heritage");
            case "womens-tops", "womens-dresses" -> List.of("Everyday", "Brunch", "Evening", "Office", "Resort", "Festive", "Classic", "Soft", "Studio");
            case "footwear" -> List.of("Daily", "Trail", "Street", "Office", "Cushion", "Flex", "Classic", "Active", "Lite");
            case "bags-wallets" -> List.of("Metro", "Travel", "Office", "Compact", "Daily", "Classic", "Premium", "Utility", "Lite");
            case "kitchen-dining" -> List.of("Family", "Compact", "Premium", "Daily", "Classic", "Steel", "Glass", "Chef", "Smart");
            case "home-appliances" -> List.of("Compact", "Smart", "Energy", "Digital", "Premium", "Classic", "Turbo", "Silent", "Max");
            case "beauty-grooming" -> List.of("Daily", "Sensitive", "Hydra", "Glow", "Matte", "Fresh", "Repair", "Pro", "Lite");
            case "fitness-sports" -> List.of("Training", "Pro", "Active", "Home", "Outdoor", "Flex", "Endurance", "Lite", "Club");
            case "books-stationery" -> List.of("Study", "Office", "Creative", "Daily", "Classic", "Premium", "Pocket", "Planner", "Archive");
            case "accessories-cables" -> List.of("Desk", "Travel", "Fast", "Compact", "Pro", "Lite", "Max", "Utility", "Everyday");
            default -> List.of("Classic", "Premium", "Daily", "Compact", "Pro", "Lite", "Max", "Urban", "Essential");
        };
        return descriptors.get((variantNumber - 1) % descriptors.size());
    }

    private static String pick(List<String> values, int index) {
        return values.get(Math.floorMod(index - 1, values.size()));
    }

    private static String slugify(String value) {
        return value.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }

    private static Product p(String slug,
                             String name,
                             String description,
                             String price,
                             String category,
                             String brand,
                             int stock,
                             Map<String, String> attributes) {
        return new Product(
                slug,
                name,
                description,
                new BigDecimal(price),
                "INR",
                category,
                brand,
                List.of(imageUrl(category, name)),
                stock,
                true,
                attributes);
    }

    private static String imageUrl(String category, String name) {
        long lock = Integer.toUnsignedLong((category + ":" + name).hashCode()) % 100_000L;
        return "https://loremflickr.com/800/600/" + photoKeyword(category, name) + "/all?lock=" + lock;
    }

    private static String photoKeyword(String category, String name) {
        String normalizedName = name.toLowerCase();
        if (normalizedName.contains("phone") || normalizedName.contains("iphone")) return "smartphone";
        if (normalizedName.contains("airpods") || normalizedName.contains("buds") || normalizedName.contains("earbuds")) return "earbuds";
        if (normalizedName.contains("headphones")) return "headphones";
        if (normalizedName.contains("macbook") || normalizedName.contains("laptop") || normalizedName.contains("xps") || normalizedName.contains("zenbook")) return "laptop";
        if (normalizedName.contains("ipad") || normalizedName.contains("tablet") || normalizedName.contains("kindle")) return "tablet";
        if (normalizedName.contains("watch")) return "watch";
        if (normalizedName.contains("shirt")) return "shirt";
        if (normalizedName.contains("t-shirt") || normalizedName.contains("tshirt")) return "tshirt";
        if (normalizedName.contains("trousers")) return "trousers";
        if (normalizedName.contains("jeans")) return "jeans";
        if (normalizedName.contains("pants") || normalizedName.contains("jogger")) return "pants";
        if (normalizedName.contains("dress")) return "dress";
        if (normalizedName.contains("shoes") || normalizedName.contains("sneakers")) return "shoes";
        if (normalizedName.contains("sandals")) return "sandals";
        if (normalizedName.contains("heels")) return "heels";
        if (normalizedName.contains("flip flops")) return "flip-flops";
        if (normalizedName.contains("backpack")) return "backpack";
        if (normalizedName.contains("tote")) return "tote-bag";
        if (normalizedName.contains("wallet")) return "wallet";
        if (normalizedName.contains("duffel")) return "duffel-bag";
        if (normalizedName.contains("sling")) return "sling-bag";
        if (normalizedName.contains("water bottle")) return "water-bottle";
        if (normalizedName.contains("fry pan")) return "frying-pan";
        if (normalizedName.contains("dinner set")) return "dinnerware";
        if (normalizedName.contains("jars")) return "glass-jars";
        if (normalizedName.contains("kettle")) return "electric-kettle";
        if (normalizedName.contains("air fryer")) return "air-fryer";
        if (normalizedName.contains("vacuum")) return "vacuum-cleaner";
        if (normalizedName.contains("fan")) return "tower-fan";
        if (normalizedName.contains("iron")) return "steam-iron";
        if (normalizedName.contains("serum")) return "face-serum";
        if (normalizedName.contains("face wash")) return "face-wash";
        if (normalizedName.contains("trimmer")) return "beard-trimmer";
        if (normalizedName.contains("lipstick")) return "lipstick";
        if (normalizedName.contains("yoga mat")) return "yoga-mat";
        if (normalizedName.contains("dumbbell")) return "dumbbell";
        if (normalizedName.contains("badminton")) return "badminton-racket";
        if (normalizedName.contains("football")) return "football";
        if (normalizedName.contains("habits") || normalizedName.contains("paperback")) return "book";
        if (normalizedName.contains("notebooks")) return "notebook";
        if (normalizedName.contains("pens")) return "pens";
        if (normalizedName.contains("desk organizer")) return "desk-organizer";
        if (normalizedName.contains("cable")) return "usb-cable";
        if (normalizedName.contains("charger")) return "charger";
        if (normalizedName.contains("mouse")) return "computer-mouse";
        if (normalizedName.contains("keyboard")) return "keyboard";
        if (normalizedName.contains("phone stand")) return "phone-stand";

        return switch (category) {
            case "smartphones" -> "smartphone";
            case "tablets" -> "tablet";
            case "laptops" -> "laptop";
            case "earbuds-headphones" -> "headphones";
            case "accessories-cables" -> "electronics";
            case "mens-shirts" -> "shirt";
            case "mens-tshirts" -> "tshirt";
            case "mens-jeans-trousers" -> "trousers";
            case "womens-tops" -> "blouse";
            case "womens-dresses" -> "dress";
            case "footwear" -> "shoes";
            case "bags-wallets" -> "bag";
            case "kitchen-dining" -> "cookware";
            case "home-appliances" -> "appliance";
            case "beauty-grooming" -> "cosmetics";
            case "fitness-sports" -> "fitness-equipment";
            case "books-stationery" -> "stationery";
            case "watches-wearables" -> "watch";
            default -> "product";
        };
    }
}
