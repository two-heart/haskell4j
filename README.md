# haskell4j

This is the source code for a CTF challenge.
If you want to attempt it yourself, don't read the code or further paragraphs of the README, but go to [the challenge](https://gpn21.ctf.kitctf.de/challenges#haskell4j-11).
Good luck!

The name "haskell4j" and the accompanying description on [the challenge page](https://gpn21.ctf.kitctf.de/challenges#haskell4j-11) are chosen humorously. In fact, there is no haskell in this project. The only thing reminding of haskell code is the function composition and currying.

Also humorouly is the author writeup for this challenge titled [Handling the Technical Interview](https://wachter-space.de/2023/11/06/handling-the-technical-interview/).

## Project overview

The main code found in [MethodHandleFun.java](https://github.com/two-heart/haskell4j/blob/main/src/main/java/de/kitctf/gpnctf2023/MethodHandleFun.java). It implements an encryption algorithm.
The unit tests in [MethodHandleFunTest.java](https://github.com/two-heart/haskell4j/blob/main/src/test/java/de/kitctf/gpnctf2023/MethodHandleFunTest.java) are useful to look at, as they implement the straight-forward equivalents of the method handle functions.

To be able to write and test parts of the code separately, they are syntactically split into separate functions. However, before release, everything is inlined using Spoon in [SpoonyProcessor.java](https://github.com/two-heart/haskell4j/blob/main/src/main/java/de/kitctf/gpnctf2023/release/SpoonyProcessor.java). For some extra fun this code also implements replacing integer constants with seeded calls to random.

## Build instructions
1. `mvn package && java -jar target/method-handles.jar`
2. A challenge jar named `out.jar` will be created
