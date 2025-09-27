// Initialize localStorage if empty
if (!localStorage.getItem('users')) localStorage.setItem('users', JSON.stringify([]));
if (!localStorage.getItem('equipments')) localStorage.setItem('equipments', JSON.stringify([]));
if (!localStorage.getItem('consumptionData')) localStorage.setItem('consumptionData', JSON.stringify([]));

// -------- LOGIN PAGE --------
const loginForm = document.getElementById('loginForm');
if (loginForm) {
    loginForm.addEventListener('submit', function (e) {
        e.preventDefault();
        const username = document.getElementById('username').value.trim();
        const password = document.getElementById('password').value.trim();
        let users = JSON.parse(localStorage.getItem('users'));
        let user = users.find(u => u.username === username && u.password === password);

        if (user) {
            localStorage.setItem('currentUserId', user.user_id);
            window.location.href = 'consumption.html';
        } else {
            alert('Invalid login details. Please register if new.');
        }
    });
}

// -------- REGISTER PAGE --------
const registerForm = document.getElementById('registerForm');
if (registerForm) {
    registerForm.addEventListener('submit', function (e) {
        e.preventDefault();
        const username = document.getElementById('regUsername').value.trim();
        const password = document.getElementById('regPassword').value.trim();

        let users = JSON.parse(localStorage.getItem('users'));
        let existing = users.find(u => u.username === username);
        if (existing) {
            alert('Username already exists. Please login.');
            return;
        }

        const newUserId = users.length + 1;
        const newUser = { user_id: newUserId, username, password };
        users.push(newUser);
        localStorage.setItem('users', JSON.stringify(users));
        localStorage.setItem('currentUserId', newUserId);
        window.location.href = 'consumption.html';
    });
}

// -------- CONSUMPTION PAGE --------
if (window.location.pathname.endsWith('consumption.html')) {
    const userId = localStorage.getItem('currentUserId');

    window.renderEquipments = function () {
        const searchTerm = document.getElementById('searchInput')?.value.toLowerCase() || '';
        const equipments = JSON.parse(localStorage.getItem('equipments'))
            .filter(eq => eq.user_id == userId && eq.equipment_name.toLowerCase().includes(searchTerm));

        const list = document.getElementById('equipmentsList');
        list.innerHTML = equipments.map(eq => {
            const today = new Date().toDateString();
            let consumptionData = JSON.parse(localStorage.getItem('consumptionData'));
            let existing = consumptionData.find(d => d.equipment_id == eq.equipment_id && d.date === today);

            if (!existing) {
                const simulatedConsumed = parseFloat((Math.random() * 10 + 1).toFixed(2));
                existing = { equipment_id: eq.equipment_id, date: today, power_consumed: simulatedConsumed };
                consumptionData.push(existing);
                localStorage.setItem('consumptionData', JSON.stringify(consumptionData));
            }

            const cost = (existing.power_consumed * 5).toFixed(2);

            return `<div>
                <strong>${eq.equipment_name}</strong> | Today's Consumption: ${existing.power_consumed} kWh | Cost: ₹${cost}
                <button onclick="deleteAppliance(${eq.equipment_id})">Delete</button>
                <button onclick="viewGraph(${eq.equipment_id})">View Graph</button>
            </div>`;
        }).join('');
    };

    window.addAppliance = function () {
        const applianceName = document.getElementById('applianceName').value.trim();
        if (applianceName) {
            let equipments = JSON.parse(localStorage.getItem('equipments'));
            const newEqId = equipments.length + 1;
            equipments.push({
                equipment_id: newEqId,
                user_id: parseInt(userId),
                equipment_name: applianceName
            });
            localStorage.setItem('equipments', JSON.stringify(equipments));
            renderEquipments();
            document.getElementById('applianceName').value = '';
        }
    };

    window.deleteAppliance = function (id) {
        let equipments = JSON.parse(localStorage.getItem('equipments')).filter(eq => eq.equipment_id != id);
        let consumptionData = JSON.parse(localStorage.getItem('consumptionData')).filter(cd => cd.equipment_id != id);
        localStorage.setItem('equipments', JSON.stringify(equipments));
        localStorage.setItem('consumptionData', JSON.stringify(consumptionData));
        renderEquipments();
    };

    window.viewGraph = function (eqId) {
        localStorage.setItem('selectedEquipment', eqId);
        window.location.href = 'graph.html';
    };

    renderEquipments();
}

// -------- GRAPH PAGE --------
if (window.location.pathname.endsWith('graph.html')) {
    const graphsContainer = document.getElementById('graphsContainer');
    const selectedEqId = localStorage.getItem('selectedEquipment');
    const consumptions = JSON.parse(localStorage.getItem('consumptionData'));
    const equipments = JSON.parse(localStorage.getItem('equipments')).filter(eq => eq.user_id == localStorage.getItem('currentUserId'));
    const eq = equipments.find(e => e.equipment_id == selectedEqId);

    function generateGraph(period = 'weekly') {
        graphsContainer.innerHTML = '';
        const canvas = document.createElement('canvas');
        graphsContainer.appendChild(canvas);

        const labels = [];
        const data = [];
        const now = new Date();
        const days = period === 'weekly' ? 7 : 30;

        for (let i = days - 1; i >= 0; i--) {
            const date = new Date(now);
            date.setDate(now.getDate() - i);
            const dateLabel = date.toLocaleDateString();
            labels.push(dateLabel);

            let record = consumptions.find(d => d.equipment_id == eq.equipment_id && d.date === dateLabel);
            if (!record) {
                record = { equipment_id: eq.equipment_id, date: dateLabel, power_consumed: parseFloat((Math.random() * 10 + 1).toFixed(2)) };
                consumptions.push(record);
                localStorage.setItem('consumptionData', JSON.stringify(consumptions));
            }
            data.push(record.power_consumed);
        }

        new Chart(canvas.getContext('2d'), {
            type: 'line',
            data: {
                labels: labels,
                datasets: [{
                    label: eq.equipment_name,
                    data: data,
                    borderColor: getRandomColor(),
                    fill: false
                }]
            },
            options: {
                responsive: true,
                plugins: {
                    title: { display: true, text: `${eq.equipment_name} Consumption (${period.toUpperCase()})` }
                },
                scales: {
                    y: { title: { display: true, text: 'Power Consumed (kWh)' } }
                }
            }
        });
    }

    window.showWeeklyGraphs = function () { generateGraph('weekly'); }
    window.showMonthlyGraphs = function () { generateGraph('monthly'); }

    function getRandomColor() {
        const r = Math.floor(Math.random() * 200 + 50);
        const g = Math.floor(Math.random() * 200 + 50);
        const b = Math.floor(Math.random() * 200 + 50);
        return `rgba(${r},${g},${b},1)`;
    }

    generateGraph('weekly');
}
