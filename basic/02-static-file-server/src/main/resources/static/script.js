// script.js - Simple interaction for static file server

// Wait for the DOM content to fully load before attaching event listeners
document.addEventListener("DOMContentLoaded", () => {
    const btn = document.getElementById("alertBtn");

    // Attach an event listener to the button
    btn.addEventListener("click", () => {
        alert("The JavaScript file was successfully loaded and executed!");
    });
});
