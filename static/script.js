let products = [];
let bill = [];

async function fetchProducts() {
  const res = await fetch('/products');
  products = await res.json();
  populateProductDropdown();
  populateProductTable();
}

function populateProductDropdown() {
  const select = document.getElementById('productSelect');
  select.innerHTML = '';
  products.forEach(p => {
    const option = document.createElement('option');
    option.value = p.id;
    option.textContent = `${p.name} (₹${p.price})`;
    select.appendChild(option);
  });
}

function populateProductTable() {
  const tableBody = document.querySelector('#productTable tbody');
  tableBody.innerHTML = '';
  products.forEach(p => {
    const row = document.createElement('tr');
    row.innerHTML = `
      <td>${p.id}</td>
      <td>${p.name}</td>
      <td>₹${p.price.toFixed(2)}</td>
      <td>${p.gst}%</td>
      <td>${p.stock}</td>
      <td>
        <input type="number" id="stock_${p.id}" value="${p.stock}" min="0" style="width:60px;">
        <button onclick="updateStock(${p.id})">Update</button>
      </td>
    `;
    tableBody.appendChild(row);
  });
}

function addToBill() {
  const productId = parseInt(document.getElementById('productSelect').value);
  const qty = parseInt(document.getElementById('quantityInput').value);
  const product = products.find(p => p.id === productId);

  if (!product || qty <= 0) return alert('Invalid selection!');
  if (qty > product.stock) return alert('Not enough stock available!');

  const total = (product.price + (product.price * product.gst / 100)) * qty;
  bill.push({ ...product, qty, total });
  updateBillTable();
}

function updateBillTable() {
  const tbody = document.querySelector('#billTable tbody');
  tbody.innerHTML = '';
  let grandTotal = 0;

  bill.forEach(item => {
    grandTotal += item.total;
    const row = document.createElement('tr');
    row.innerHTML = `
      <td>${item.id}</td>
      <td>${item.name}</td>
      <td>₹${item.price.toFixed(2)}</td>
      <td>${item.gst}%</td>
      <td>${item.qty}</td>
      <td>₹${item.total.toFixed(2)}</td>
    `;
    tbody.appendChild(row);
  });

  document.getElementById('grandTotal').textContent =
    `Grand Total: ₹${grandTotal.toFixed(2)}`;
}

async function updateStock(id) {
  const newStock = document.getElementById(`stock_${id}`).value;

  const res = await fetch('/updateStock', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: `id=${id}&stock=${newStock}`
  });

  const data = await res.json();
  if (data.success) {
    alert('Stock updated successfully!');
    fetchProducts();
  } else {
    alert('Failed to update stock.');
  }
}

document.getElementById('addToBill').addEventListener('click', addToBill);
fetchProducts();
