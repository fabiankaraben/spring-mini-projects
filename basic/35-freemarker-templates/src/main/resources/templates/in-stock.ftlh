<#--
    FreeMarker Template: in-stock.ftlh
    =====================================
    Renders the page that lists only in-stock products.

    Data-model variables supplied by ProductController.inStock():
      - products : List of Product objects (only those where inStock == true)

    FreeMarker features used:
      - products?size  : returns the number of elements in the sequence
      - #list          : iterates over the sequence

    Java Record Accessor Methods:
    Java records expose components as non-JavaBean methods (name() not getName()).
    FreeMarker requires explicit call syntax: product.name(), product.category()
    instead of product.name, product.category.

    Auto-escaping:
      The .ftlh extension activates auto-escaping for HTML output, so
      ${...} interpolations are automatically HTML-safe — no ?html needed.
-->
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>In Stock | FreeMarker Templates</title>

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

        .container { max-width: 1100px; margin: 2rem auto; padding: 0 1rem; }

        .page-header {
            display: flex;
            align-items: center;
            gap: 1rem;
            margin-bottom: 1.5rem;
        }

        h1 { font-size: 1.8rem; color: #0d1b2a; }

        .count-badge {
            background: #1b998b;
            color: #fff;
            font-size: 0.85rem;
            font-weight: 700;
            padding: 0.25rem 0.75rem;
            border-radius: 20px;
        }

        .grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
            gap: 1.25rem;
        }

        .card {
            background: #fff;
            border-radius: 12px;
            padding: 1.25rem;
            box-shadow: 0 2px 8px rgba(0,0,0,0.07);
            transition: transform 0.2s, box-shadow 0.2s;
            display: flex;
            flex-direction: column;
        }

        .card:hover {
            transform: translateY(-4px);
            box-shadow: 0 8px 20px rgba(0,0,0,0.12);
        }

        .card-category {
            font-size: 0.75rem;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.08em;
            color: #1b998b;
            margin-bottom: 0.4rem;
        }

        .card-title { font-size: 1.1rem; font-weight: 700; margin-bottom: 0.5rem; color: #0d1b2a; }

        .card-price {
            font-size: 1.25rem;
            font-weight: 600;
            color: #333;
            margin-top: auto;
            padding-top: 0.75rem;
        }

        .badge-in-stock {
            display: inline-block;
            font-size: 0.75rem;
            font-weight: 600;
            padding: 0.2rem 0.6rem;
            border-radius: 20px;
            margin-top: 0.5rem;
            background: #d4edda;
            color: #155724;
        }

        .card-link {
            display: inline-block;
            margin-top: 1rem;
            color: #1b998b;
            font-weight: 600;
            text-decoration: none;
            font-size: 0.9rem;
            transition: color 0.2s;
        }

        .card-link:hover { color: #13706a; }
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
    <div class="page-header">
        <h1>Available Products</h1>
        <#--
            products?size is a FreeMarker sequence built-in equivalent to
            products.size() in Java. It renders the count inside the badge.
        -->
        <span class="count-badge">${products?size} items</span>
    </div>

    <div class="grid">
        <#--
            The #list directive iterates over the 'products' model attribute.
            Java record methods must be called with explicit parentheses:
              product.category()  ->  calls the record component accessor
              product.name()      ->  calls the record component accessor
            Without parentheses, FreeMarker sees a method+sequence wrapper
            and cannot convert it to a string for interpolation.
        -->
        <#list products as product>
            <div class="card">
                <div class="card-category">${product.category()}</div>
                <div class="card-title">${product.name()}</div>
                <div class="card-price">$${product.price()?string("0.00")}</div>
                <span class="badge-in-stock">✔ In Stock</span>
                <a class="card-link" href="/products/${product.id()}">View Details →</a>
            </div>
        </#list>
    </div>
</div>
</body>
</html>
