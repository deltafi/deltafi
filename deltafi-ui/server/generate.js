var faker = require('faker');
//TODO modify this to generate pods and nodes
var database = { certificates: [] };

for (var i = 1; i<= 10; i++) {
  database.certificates.push({
    serial_number: i,
    dn: "CN=" + faker.internet.domainWord() + faker.internet.domainName() + faker.internet.domainSuffix() + ",OU=Servers,OU=Sub2,OU=Sub1,O=U.S. Government Test,C=US",
    issuer: "CN=" + faker.internet.userAgent() + ",OU=Servers,OU=Sub2,OU=Sub1,O=U.S. Government Test,C=US",
    not_valid_before: faker.date.past(),
    not_valid_after: faker.date.future(),
  });
 /*  database.requestor.push({
    requested_by: faker.name.lastName() + ", " +  faker.name.firstName(),
  }); */
}

console.log(JSON.stringify(database));

