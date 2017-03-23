function dom(element) {
    return document.getElementById(element);
}

var crn = {};

crn.nodes = null;
crn.map = null;
crn.markerClusterer = null;
crn.markers = [];
crn.infoWindow = null;

crn.init = function() {
    var latlng = new google.maps.LatLng(45.919636,14.221162);
    var options = {
        'zoom': 14,
        'center': latlng,
        'mapTypeId': google.maps.MapTypeId.ROADMAP
    };

    crn.map = new google.maps.Map(dom('map'), options);
    crn.nodes = logatec_data.nodes;  

    crn.infoWindow = new google.maps.InfoWindow();

    crn.showMarkers();
    crn.markerClusterer = new MarkerClusterer(crn.map, crn.markers);
};

crn.showMarkers = function() {
    crn.markers = [];

    if (crn.markerClusterer) {
        crn.markerClusterer.clearMarkers();
    }

    var panel = {};
    panel = dom('markerlist');
    panel.innerHTML = '';   
    var numMarkers = logatec_data.count;

    for (var i = 0; i < numMarkers; i++) {
        var titleText = crn.nodes[i].node_title;
        if (titleText == '') {
            titleText = 'No title';
        }

        var item = document.createElement('DIV');
        var title = document.createElement('A');
        title.href = '#';
        title.className = 'title';
        title.innerHTML = titleText;

        item.appendChild(title);
        panel.appendChild(item);


        var latLng = new google.maps.LatLng(crn.nodes[i].latitude,
            crn.nodes[i].longitude);

        var imageUrl = 'http://chart.apis.google.com/chart?cht=mm&chs=24x32&chco=' +
        'FFFFFF,008CFF,000000&ext=.png';
        var markerImage = new google.maps.MarkerImage(imageUrl,
            new google.maps.Size(24, 32));

        var marker = new google.maps.Marker({
            'position': latLng,
            'icon': markerImage
        });

        var fn = crn.markerClickFunction(crn.nodes[i], latLng);
        google.maps.event.addListener(marker, 'click', fn);
        google.maps.event.addDomListener(title, 'click', fn);
        crn.markers.push(marker);
    }

    crn.markerClusterer = new MarkerClusterer(crn.map, crn.markers);
};

crn.markerClickFunction = function(node, latlng) {
    return function(e) {
        e.cancelBubble = true;
        e.returnValue = false;
        if (e.stopPropagation) {
            e.stopPropagation();
            e.preventDefault();
        }
        var title = node.node_title;
        var cluster = node.cluster;

        var infoHtml = '<div class="info"><h2>' + title +
        '</h2><h3>' + cluster +
        '</h3>';

        crn.infoWindow.setContent(infoHtml);
        crn.infoWindow.setPosition(latlng);
        crn.infoWindow.open(crn.map);
    };
};

crn.clear = function() {  
    for (var i = 0, marker; marker = crn.markers[i]; i++) {
        marker.setMap(null);
    }
};

crn.change = function() {
    crn.clear();
    crn.showMarkers();
};
