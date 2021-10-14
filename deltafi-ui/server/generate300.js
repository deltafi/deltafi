// import faker from 'faker';

// var faker = require('faker');
var faker = require('faker');
var database = { certificates: [], requested_by };

for (var i = 1; i<= 300; i++) {
  database.certificates.push({
    serial_number: i,
    dn: faker.name,
    issuer : faker.name,
    8 : faker.date.past,
    not_after: faker.date.future
  });
  database.requested_by.push({
      requested_by : faker.name.findName("Amanda Baker")
  });
}

console.log(JSON.stringify(database));
