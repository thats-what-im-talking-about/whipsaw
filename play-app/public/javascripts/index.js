const form = document.querySelector('form');
const name = document.querySelector('#name');
const items = document.querySelector('#items');
const error = document.querySelector('#error');

form.addEventListener('submit', (evt) => {
  evt.preventDefault();

  if(name.value && items.value) {
    const workload = {
      'name': name.value,
      'items': parseInt(items.value)
    };

    var xhr = new XMLHttpRequest();
    xhr.open('POST', '/launch');
    xhr.setRequestHeader("Content-Type", "application/json; charset=UTF-8");
    xhr.onreadystatechange = function () {
      if (xhr.readyState === 4 && xhr.status === 200) {
        var res = JSON.parse(xhr.response);
        console.log(res);
        socket.send("addWorkloadId:" + res)
      }
      name.value = "";
      items.value = "";
      error.textContent = "";
    };
    xhr.send(JSON.stringify(workload));
  } else {
    error.textContent = "Please enter a name and number of items."
  }
})
