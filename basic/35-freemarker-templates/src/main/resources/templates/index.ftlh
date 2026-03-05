<#--
    FreeMarker Template: index.ftlh
    ================================
    This template renders the main product catalog page.

    FreeMarker Syntax Primer:
    - ${expression}      : interpolation — evaluates an expression and inserts the result.
    - #if cond           : conditional block (written as <#if> in templates).
    - #list seq as x     : loop over a sequence (written as <#list> in templates).
    - ?size              : built-in that returns the size of a sequence.
    - ?has_content       : built-in that returns false for null, empty string, or empty list.
    - ?url               : built-in that URL-encodes a string for safe use in href attributes.
    - ?string("pattern") : built-in that formats a number using a Java DecimalFormat pattern.
    - ?then(a, b)        : ternary-style built-in: returns a if the boolean is true, b otherwise.

    Java Record Accessor Methods:
    Java records expose components as no-arg methods that do NOT follow the
    JavaBean naming convention (e.g., name() instead of getName()). FreeMarker
    resolves "product.name" ambiguously to a method+sequence wrapper. To call
    such non-JavaBean accessors, we must use explicit parentheses in FTL:
      ${product.name()}     ->  calls Product.name()  (record component accessor)
    This is distinct from JavaBean-style ${product.name}, which would call getName().

    Auto-escaping in .ftlh files:
    The .ftlh extension activates FreeMarker's "HTML" output format, which enables
    AUTO-ESCAPING for all ${...} interpolations. Using ?html when auto-escaping is
    already active causes a parse error, so it must be omitted.

    FreeMarker Comments:
    Use FreeMarker comments (#-- --) for notes that describe FTL syntax.
    HTML comments (<!-- -->) that contain angle-bracket FTL tags will cause
    parse errors in .ftlh mode — FreeMarker still processes them.

    Template Resolution:
    Spring MVC returns the view name "index" from the controller.
    The FreeMarker ViewResolver appends ".ftlh" and looks for:
        classpath:/templates/index.ftlh
-->
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Product Catalog | FreeMarker Templates</title>

    <!-- Inline CSS to keep the project self-contained (no external assets needed) -->
    <style>
        /* ---- Reset & base ---- */
        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: #f0f2f5;
            color: #333;
            min-height: 100vh;
        }

        /* ---- Navigation ---- */
        nav {
            background: #0d1b2a;
            padding: 1rem 2rem;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }

        nav .brand {
            color: #1b998b;
            font-size: 1.4rem;
            font-weight: 700;
            text-decoration: none;
        }

        nav a {
            color: #ccc;
            text-decoration: none;
            margin-left: 1.5rem;
            transition: color 0.2s;
        }

        nav a:hover { color: #fff; }

        /* ---- Page container ---- */
        .container {
            max-width: 1100px;
            margin: 2rem auto;
            padding: 0 1rem;
        }

        h1 { margin-bottom: 1.5rem; font-size: 1.8rem; color: #0d1b2a; }

        /* ---- Category filter bar ---- */
        .filter-bar {
            display: flex;
            flex-wrap: wrap;
            gap: 0.5rem;
            margin-bottom: 1.5rem;
        }

        .filter-bar a {
            padding: 0.4rem 1rem;
            border-radius: 20px;
            text-decoration: none;
            font-size: 0.9rem;
            border: 2px solid #0d1b2a;
            color: #0d1b2a;
            transition: background 0.2s, color 0.2s;
        }

        .filter-bar a:hover,
        .filter-bar a.active {
            background: #0d1b2a;
            color: #fff;
        }

        /* ---- Product grid ---- */
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

        .card-title {
            font-size: 1.1rem;
            font-weight: 700;
            margin-bottom: 0.5rem;
            color: #0d1b2a;
        }

        .card-price {
            font-size: 1.25rem;
            font-weight: 600;
            color: #333;
            margin-top: auto;
            padding-top: 0.75rem;
        }

        /* ---- Stock badge ---- */
        .badge {
            display: inline-block;
            font-size: 0.75rem;
            font-weight: 600;
            padding: 0.2rem 0.6rem;
            border-radius: 20px;
            margin-top: 0.5rem;
        }

        .badge-in-stock  { background: #d4edda; color: #155724; }
        .badge-out-stock { background: #f8d7da; color: #721c24; }

        /* ---- Detail link ---- */
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

        /* ---- Empty state ---- */
        .empty {
            text-align: center;
            padding: 3rem;
            color: #888;
            font-size: 1.1rem;
        }
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
    <h1>Product Catalog</h1>

    <#--
        ---- Category filter bar ----

        The ?has_content built-in returns false for null, empty strings, and
        empty lists. selectedCategory is null when no filter is active.

        IMPORTANT: In .ftlh (HTML output format) FreeMarker does NOT allow
        FTL directives inside HTML attribute values. The workaround is to
        pre-compute the class string with the #assign directive and output it
        via a plain ${...} interpolation.

        The ?then(a, b) built-in is the FreeMarker ternary operator:
          booleanExpr?then("value-if-true", "value-if-false")
    -->
    <div class="filter-bar">
        <#assign allActiveClass = (!selectedCategory?has_content)?then("active", "")>
        <a href="/" class="${allActiveClass}">All</a>

        <#--
            Iterate over each category to build filter pills.
            cat?url URL-encodes the category name for the href.
            Each category is a plain String, so ${cat} works directly.
            Pre-compute the active class to avoid directives inside attributes.
        -->
        <#list categories as cat>
            <#assign catActiveClass = (selectedCategory?has_content && selectedCategory == cat)?then("active", "")>
            <a href="/?category=${cat?url}" class="${catActiveClass}">${cat}</a>
        </#list>
    </div>

    <#--
        ---- Product grid ----
        The #if directive with ?has_content guards the grid when the
        list is non-null and non-empty.
    -->
    <#if products?has_content>
        <div class="grid">
            <#--
                The #list directive iterates over the 'products' model attribute.

                Java records expose accessors as non-JavaBean methods, e.g.
                name() instead of getName(). FreeMarker requires explicit call
                syntax for such methods: product.name() not product.name.
                Without parentheses, FreeMarker evaluates "product.name" as a
                method+sequence wrapper and cannot coerce it to a string.
            -->
            <#list products as product>
                <div class="card">
                    <#-- product.category() calls the record component accessor -->
                    <div class="card-category">${product.category()}</div>
                    <div class="card-title">${product.name()}</div>

                    <#--
                        ?string("0.00") formats the price using a DecimalFormat
                        pattern, resulting in exactly two decimal places.
                        product.price() returns a double primitive.
                    -->
                    <div class="card-price">$${product.price()?string("0.00")}</div>

                    <#-- product.inStock() returns a boolean — used directly in #if -->
                    <#if product.inStock()>
                        <span class="badge badge-in-stock">In Stock</span>
                    <#else>
                        <span class="badge badge-out-stock">Out of Stock</span>
                    </#if>

                    <a class="card-link" href="/products/${product.id()}">View Details →</a>
                </div>
            </#list>
        </div>
    <#else>
        <div class="empty">
            <p>No products found for this filter. <a href="/">View all</a></p>
        </div>
    </#if>

</div>
</body>
</html>
