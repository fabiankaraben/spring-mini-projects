<#--
    FreeMarker Template: not-found.ftlh
    ======================================
    Rendered when a product ID is not found in the catalog.

    Data-model variables supplied by ProductController.productDetail():
      - message : String — a human-readable error message, e.g.
                  "Product with ID 999 was not found."

    FreeMarker features used:
      - ${message} : safe interpolation (auto-escaped in .ftlh files)

    Auto-escaping:
      The .ftlh extension enables auto-escaping so ${message} is
      automatically HTML-safe — no ?html needed or allowed.
-->
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Not Found | FreeMarker Templates</title>

    <style>
        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: #f0f2f5;
            color: #333;
            min-height: 100vh;
            display: flex;
            flex-direction: column;
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

        .container {
            flex: 1;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            text-align: center;
            padding: 2rem;
        }

        .icon { font-size: 5rem; margin-bottom: 1rem; }

        h1 { font-size: 2.5rem; color: #0d1b2a; margin-bottom: 0.75rem; }

        .message {
            font-size: 1.1rem;
            color: #555;
            margin-bottom: 2rem;
            max-width: 480px;
        }

        .btn-home {
            display: inline-block;
            background: #1b998b;
            color: #fff;
            text-decoration: none;
            padding: 0.75rem 2rem;
            border-radius: 8px;
            font-weight: 600;
            transition: background 0.2s;
        }

        .btn-home:hover { background: #13706a; }
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
    <div class="icon">🔍</div>
    <h1>Product Not Found</h1>
    <#--
        ${message} outputs the error string passed by the controller.
        'message' is a plain String (not a record accessor), so no
        parentheses are needed. Auto-escaping converts any HTML
        special characters to their safe HTML entity equivalents.
    -->
    <p class="message">${message}</p>
    <a class="btn-home" href="/">← Back to Catalog</a>
</div>
</body>
</html>
