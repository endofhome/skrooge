function sendFakeFormData() {
    var dest = "http://localhost:5000/statements";
    var xhr = new XMLHttpRequest();
    xhr.open("POST", dest, true);
    xhr.onreadystatechange = function () {
        if(xhr.readyState === XMLHttpRequest.DONE && xhr.status === 200) {
            var body = document.getElementById('body');
            body.innerHTML = xhr.responseText;
        }
    };
    var body = "2017;September;Tom;[input/normalised/2017-02_Someone_Monzo.csv]";
    xhr.send(body);
}

document.getElementById("upload").onclick = function () {
    sendFakeFormData();
};
