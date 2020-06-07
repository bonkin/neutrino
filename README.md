# Installation

Firstly, build OpenRQ dependency
1. `cd ..`
2. `git clone https://github.com/redjaguar/OpenRQ.git`
3. `cd OpenRQ`
4. `mvn install`

Than this project should be compilable
1. `cd ../neutrino`
2. `mvn spring-boot:run`

After all, check or visit localhost:8080
1. `curl -X HEAD -I localhost:8080`
