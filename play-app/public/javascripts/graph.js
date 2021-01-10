const dims = { height: 300, width: 300 };
const cent = { x: (dims.width / 2 + 5), y: (dims.height / 2 + 5) };

const svg = d3.select('.canvas')
  .append('svg')
  .attr('width', dims.width + 150)
  .attr('height', dims.height + 150);

const graph = svg.append('g')
  .attr('transform', `translate(${cent.x}, ${cent.y})`);

const pie = d3.pie()
  .sort(null)
  .value(d => d.items);

const radiusScale = d3.scaleLinear().range([75, 150]).domain([0, 9]);

const radius = function(data) {
  //console.log("radius.data", data);
  const total = data.filter(d => Number.isInteger(d.items)).map(d => d.items).reduce((a,b) => a+b);
  const radius = radiusScale(Math.min(9, Math.log10(total)));
  return radius;
};

const socketData = {"dbfd27d5-174c-44d9-aef8-fae8d0cd9aff":{"scheduled":0,"running":0,"scheduledForRetry":7,"completed":193,"error":0,"runAt":"2021-01-10T00:12:37.621Z"}}

var data = [];

const translate = function(id, obj) {
  var arr = Object.keys(obj).filter(k => k != "runAt").map(key => ({name: key, items: obj[key]}));
  return arr;
}

const update = function(rawData) {
  const translatedData = Object.keys(rawData).map(id => translate(id, rawData[id]) );
  data = translatedData[0];
  const angles = pie(data);
  const outerRadius = radius(data);
  const arcPath = d3.arc()
    .outerRadius(outerRadius)
    .innerRadius(outerRadius / 2);

  const paths = graph.selectAll('path').data(angles)

  paths.enter()
    .append('path')
      .attr('d', arcPath)
      .attr('class', d => `${d.data.name} items`)
      .attr('stroke', '#fff');

  paths.attr('d', arcPath);

  console.log("update.data", data);
  console.log("update.angles", angles);
  console.log("update.outerRadius", outerRadius);
}

//update(socketData);
socket.onmessage = function(event) {
  console.log("rawdata", JSON.parse(event.data));
  update(JSON.parse(event.data));
}
