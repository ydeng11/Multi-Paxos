# Multi-Paxos

This project implemented multi-paxos based on Diego Ongaro's [lecture](https://youtu.be/JEpsBg0AO6o) which is very beautiful and highly recommended.
My [perception and summary](https://ihelio.today/archives/theelegantconsensusalgorithm-multi-paxos-injava) is documented in my blog.

Thus, I will mainly talk about my implementation here and skip the algorithm part then.

## Structure

This service is composed of three sub-service:

    - Leader Election
    - Consensus Algorithm
    - Data Persistent

### Leader Election
To simplify the leader election, I put the port used in the test under resource folder thus it exposes to all running host. It is a stupid and simple way
to mimic a service discovery for this service. That being said, each host will send heart beat to all other hosts and agree on one single leader to make the 
proposal and accept message (check the video or my blog for the unfamiliar terms). 

### Consensus Algorithm
After leader is elected, we could send the `dataInsertionRequest` to one of the running host which would process it or redirect it to the leader.
The consensus algorithm is mainly executed inside the leader host then.

### Data Persistent
There is another task keep writing the hash value of accepted values to local files for comparison.

## How to run it
I started three hosts using different ports and start `PaxosClient` to send the data requests. In my test, the hash value are consistent though there is 
trivial time difference. If we stop sending the requests, the hash are the same across the three hosts.

I didn't try with more hosts because leader is pretty stable thus it will work with more hosts if it works with three. On another hand, I didn't
implement it in a good way for complex tests. A better service discovery mechanism is preferred for more complex tests.

I think one beauty of this implementation is we don't have to use a lot of locking since everything is handled by the leader which largely reduce 
the competition for the same resource.
