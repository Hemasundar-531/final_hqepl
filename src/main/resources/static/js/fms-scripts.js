const FMS_API = "/admin/api/fms";

console.log("FMS Scripts Loaded");

function openModal() {
    console.log("Opening FMS Modal");
    const modal = document.getElementById("fmsModal");
    if (modal) {
        modal.style.display = "block";
        const input = document.getElementById("fmsNameInput");
        if (input) {
            input.value = "";
            input.focus();
        }
        hideMessage("createFmsError");
        hideMessage("createFmsSuccess");
    } else {
        console.error("FMS Modal element not found!");
    }
}

function closeModal() {
    const modal = document.getElementById("fmsModal");
    if (modal) modal.style.display = "none";
    const input = document.getElementById("fmsNameInput");
    if (input) input.value = "";
    hideMessage("createFmsError");
    hideMessage("createFmsSuccess");
}

function showError(msg) {
    const el = document.getElementById("createFmsError");
    if (el) {
        el.textContent = msg || "Something went wrong.";
        el.classList.add("show");
    }
    const successEl = document.getElementById("createFmsSuccess");
    if (successEl) successEl.classList.remove("show");
}

function showSuccess(msg) {
    const el = document.getElementById("createFmsSuccess");
    if (el) {
        el.textContent = msg || "FMS created successfully.";
        el.classList.add("show");
    }
    const errorEl = document.getElementById("createFmsError");
    if (errorEl) errorEl.classList.remove("show");
}

function hideMessage(id) {
    const el = document.getElementById(id);
    if (el) {
        el.classList.remove("show");
        el.textContent = "";
    }
}

async function submitCreateFms() {
    const input = document.getElementById("fmsNameInput");
    if (!input) return;

    const name = (input.value || "").trim();
    hideMessage("createFmsError");
    hideMessage("createFmsSuccess");

    if (!name) {
        showError("FMS name is required.");
        input.focus();
        return;
    }

    const submitBtn = document.getElementById("createFmsSubmit");
    if (submitBtn) submitBtn.disabled = true;

    try {
        const res = await fetch(FMS_API, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name: name })
        });

        const data = await res.json().catch(function () { return {}; });

        if (res.ok) {
            showSuccess("FMS created successfully.");
            setTimeout(function () {
                closeModal();
                window.location.reload();
            }, 800);
            return;
        }

        if (res.status === 409 || (data && data.error)) {
            showError(data.error || "A folder with this name already exists.");
            input.focus();
            return;
        }

        showError(data.error || "Failed to create FMS. Please try again.");
        input.focus();
    } catch (e) {
        console.error(e);
        showError("Network error. Please try again.");
        input.focus();
    } finally {
        if (submitBtn) submitBtn.disabled = false;
    }
}

// Global Menu & Access Modal Logic

function toggleMenu(button) {
    const panel = button.parentElement.querySelector(".menu-panel");
    const isOpen = panel.style.display === "block";
    document.querySelectorAll(".menu-panel").forEach(p => p.style.display = "none");
    panel.style.display = isOpen ? "none" : "block";
}

// Close menus when clicking outside
document.addEventListener("click", function (event) {
    if (!event.target.closest(".folder-menu")) {
        document.querySelectorAll(".menu-panel").forEach(p => p.style.display = "none");
    }
});

// Setup event listeners for FMS creation
document.addEventListener("DOMContentLoaded", function () {
    const submitBtn = document.getElementById("createFmsSubmit");
    if (submitBtn) {
        submitBtn.addEventListener("click", submitCreateFms);
    }

    const input = document.getElementById("fmsNameInput");
    if (input) {
        input.addEventListener("keydown", function (e) {
            if (e.key === "Enter") { e.preventDefault(); submitCreateFms(); }
        });
    }
});


// --- MANAGE ACCESS ---
let currentFolderId = null;

function openAccessModal(folderId) {
    console.log("Opening Access Modal for " + folderId);
    currentFolderId = folderId;
    const modal = document.getElementById("accessModal");
    if (modal) {
        modal.style.display = "block";
    } else {
        console.error("Error: Access Modal not found!");
        alert("Error: Access Modal not found in the DOM.");
    }
    loadEmployees(folderId);
}

function closeAccessModal() {
    const modal = document.getElementById("accessModal");
    if (modal) modal.style.display = "none";
    currentFolderId = null;
}

async function loadEmployees(folderId) {
    const listContainer = document.getElementById("employeeAccessList");
    if (!listContainer) return;

    listContainer.innerHTML = '<p style="color:#6b7b8c; font-size:12px;">Loading employees...</p>';

    try {
        const res = await fetch(`/admin/api/folder-access?folderId=${folderId}`);
        if (!res.ok) throw new Error("Failed to load");
        const employees = await res.json();

        listContainer.innerHTML = "";
        if (employees.length === 0) {
            listContainer.innerHTML = '<p class="info-box">No employees found.</p>';
            return;
        }

        employees.forEach(emp => {
            const div = document.createElement("div");
            div.style.cssText = "display:flex; align-items:center; gap:10px; padding:8px 0; border-bottom:1px solid #f0f0f0;";

            const checkbox = document.createElement("input");
            checkbox.type = "checkbox";
            checkbox.style.width = "16px";
            checkbox.id = `emp_${emp.username}`;
            checkbox.checked = emp.hasAccess;
            checkbox.value = emp.username;

            const label = document.createElement("label");
            label.htmlFor = `emp_${emp.username}`;
            label.style.fontSize = "13px";
            label.style.cursor = "pointer";
            label.style.flex = "1";
            label.textContent = `${emp.username} (${emp.role})`;

            div.appendChild(checkbox);
            div.appendChild(label);
            listContainer.appendChild(div);
        });

    } catch (e) {
        console.error(e);
        listContainer.innerHTML = '<p class="info-box" style="color:red;">Error loading employees.</p>';
    }
}

async function saveAccess() {
    if (!currentFolderId) return;

    const saveBtn = document.getElementById("saveAccessBtn");
    if (saveBtn) {
        saveBtn.disabled = true;
        saveBtn.innerText = "Saving...";
    }

    const checkboxes = document.querySelectorAll("#employeeAccessList input[type='checkbox']");
    const accessMap = {};
    checkboxes.forEach(cb => {
        accessMap[cb.value] = cb.checked;
    });

    try {
        const res = await fetch("/admin/api/folder-access", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                folderId: currentFolderId,
                access: accessMap
            })
        });

        if (res.ok) {
            closeAccessModal();
            alert("Access updated successfully!");
        } else {
            alert("Failed to update access.");
        }
    } catch (e) {
        console.error(e);
        alert("Error saving access.");
    } finally {
        if (saveBtn) {
            saveBtn.disabled = false;
            saveBtn.innerHTML = `
                <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M9 19a1 1 0 0 1-.7-.3l-4-4a1 1 0 1 1 1.4-1.4l3.3 3.3 8.3-8.3a1 1 0 1 1 1.4 1.4l-9 9a1 1 0 0 1-.7.3Z"/>
                </svg>
                Save Changes`;
        }
    }
}
