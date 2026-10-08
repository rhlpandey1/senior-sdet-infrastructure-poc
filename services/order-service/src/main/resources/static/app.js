const orderIdInput = document.getElementById("orderId");
const searchButton = document.getElementById("searchButton");
const loading = document.getElementById("loading");
const errorMessage = document.getElementById("errorMessage");
const orderDetails = document.getElementById("orderDetails");

searchButton.addEventListener("click", searchOrder);

orderIdInput.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
        searchOrder();
    }
});

async function searchOrder() {
    const orderId = orderIdInput.value.trim();

    hide(errorMessage);
    hide(orderDetails);

    if (!orderId) {
        showError("Please enter an Order ID");
        return;
    }

    show(loading);

    try {
        const response = await fetch(`/orders/${encodeURIComponent(orderId)}`, {
            headers: {
                "Authorization": "Basic " + btoa("sdet:sdet123")
            }
        });

        if (!response.ok) {
            if (response.status === 404) {
                throw new Error("Order not found");
            }

            if (response.status === 401) {
                throw new Error("Authentication failed");
            }

            throw new Error("Unable to retrieve order");
        }

        const order = await response.json();

        document.getElementById("orderIdValue").textContent = order.id;
        document.getElementById("customerValue").textContent = order.customerName;
        document.getElementById("productValue").textContent = order.product;
        document.getElementById("quantityValue").textContent = order.quantity;
        document.getElementById("statusValue").textContent = order.status;

        show(orderDetails);

    } catch (error) {
        showError(error.message);
    } finally {
        hide(loading);
    }
}

function show(element) {
    element.classList.remove("hidden");
}

function hide(element) {
    element.classList.add("hidden");
}

function showError(message) {
    errorMessage.textContent = message;
    show(errorMessage);
}
