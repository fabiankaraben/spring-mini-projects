<#--
    FreeMarker Template: product-detail.ftlh
    ==========================================
    Renders the detail page for a single product.

    Data-model variables supplied by ProductController.productDetail():
      - product : Product record (id, name, category, price, inStock)

    Java Record Accessor Methods:
    Java records expose components as no-arg methods that do NOT follow the
    JavaBean naming convention (name() instead of getName()). FreeMarker
    requires explicit call syntax to invoke them:
      ${product.name()}     ->  calls Product.name()
      ${product.category()} ->  calls Product.category()
    Without parentheses, FreeMarker sees "product.name" as a method+sequence
    wrapper and cannot coerce it to a string, causing a runtime error.

    Auto-escaping:
      The .ftlh extension enables HTML auto-escaping for all ${...}
      interpolations — no ?html built-in required or allowed.
-->
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${product.name()} | FreeMarker Templates</title>

    <style>
        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: #f0f2f5;
            color: #333;
            min-height: 100vh;
        }

        nav {
            background: #0d1b2a;
            padding: 1rem 2rem;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }

        nav .brand { color: #1b998b; font-size: 1.4rem; font-weight: 700; text-decoration: none; }
        nav a { color: #ccc; text-decoration: none; margin-left: 1.5rem; transition: color 0.2s; }
        nav a:hover { color: #fff; }

        .container { max-width: 700px; margin: 2rem auto; padding: 0 1rem; }

        .back-link {
            display: inline-block;
            margin-bottom: 1.5rem;
            color: #1b998b;
            text-decoration: none;
            font-weight: 600;
            transition: color 0.2s;
        }
        .back-link:hover { color: #13706a; }

        .card {
            background: #fff;
            border-radius: 16px;
            padding: 2rem;
            box-shadow: 0 4px 16px rgba(0,0,0,0.09);
        }

        .card-category {
            font-size: 0.8rem;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.1em;
            color: #1b998b;
            margin-bottom: 0.5rem;
        }

        h1 { font-size: 2rem; color: #0d1b2a; margin-bottom: 1rem; }

        .price {
            font-size: 2rem;
            font-weight: 700;
            color: #0d1b2a;
            margin-bottom: 1.5rem;
        }

        .badge {
            display: inline-block;
            font-size: 0.85rem;
            font-weight: 600;
            padding: 0.3rem 0.9rem;
            border-radius: 20px;
            margin-bottom: 1.5rem;
        }

        .badge-in-stock  { background: #d4edda; color: #155724; }
        .badge-out-stock { background: #f8d7da; color: #721c24; }

        .meta-row {
            font-size: 0.9rem;
            color: #555;
            border-top: 1px solid #eee;
            padding-top: 1rem;
        }

        .meta-row span { font-weight: 600; color: #333; }
    </style>
</head>
<body>

<nav>
    <a class="brand" href="/">🛍️ Product Catalog</a>
    <div>
        <a href="/">Home</a>
        <a href="/products/in-stock">In Stock</a>
    </div>
</nav>

<div class="container">
    <a class="back-link" href="/">← Back to Catalog</a>

    <div class="card">
        <#-- product.category() calls the Java record component accessor category() -->
        <div class="card-category">${product.category()}</div>

        <#-- product.name() calls the record component accessor name() -->
        <h1>${product.name()}</h1>

        <#--
            product.price() returns a double.
            ?string("0.00") formats it with exactly two decimal places.
        -->
        <div class="price">$${product.price()?string("0.00")}</div>

        <#--
            product.inStock() returns a boolean.
            The #if / #else / /#if block renders the appropriate badge.
        -->
        <#if product.inStock()>
            <span class="badge badge-in-stock">✔ In Stock</span>
        <#else>
            <span class="badge badge-out-stock">✘ Out of Stock</span>
        </#if>

        <div class="meta-row">Product ID: <span>#${product.id()}</span></div>
    </div>
</div>
</body>
</html>
