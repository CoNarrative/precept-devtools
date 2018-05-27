# precept-devtools
**alpha preview**

![image](https://user-images.githubusercontent.com/9045165/40589524-7f861b40-61a3-11e8-95aa-77734c317112.png)

Features:

- Event log
- Event explanations
- Color-coded fact/condition matches
- Rule history
- Fact history
- Time travel
- Realtime updates

## Getting started

Build client:
```bash
./build.sh
```

Start server:
```bash
lein run
open localhost:3232
```

If you'd prefer to run and serve the client with Figwheel:
```bash
lein run
cd precept-visualizer
lein figwheel
open localhost:3450
```
You should be able to connect to the Figwheel nREPL server at `localhost:7003` and 
access a CLJS REPL with `(cljs)`.

If you don't have a Precept app running, you won't see much. As of `precept 0.5.0-alpha`, 
`precept.core/start!` accepts devtools options that allow the application to send socket messages 
to the devtools server at its default location (`localhost:3232`).

As of this writing, the easiest way to see what the devtools are capable of is probably to 
run the latest todomvc or fullstack example from the Precept repository:

todomvc:
```bash
cd precept/examples/todomvc
lein figwheel
```

fullstack:
```bash
cd precept/examples/fullstack
lein run
lein figwheel
```

If you have an existing Precept app and are using `0.5.0-alpha` or above, you can connect to 
the devtools server by passing `{:devtools true}` to `precept.core/start!`, or supply your own 
configuration options to connect to a server on a different host and port.


## Overview
The devtools are comprised of a Clojurescript client and Clojure server. The architecture is primarily push-based. Most communication happens via socket. 
There are the beginnings of a REST API -- `GET /log/range/:n1/:n2` -- which returns EDN data for states within a requested window.

The server receives a considerable amount of information from running Precept applications, including each rule definition and the schema being enforced. 

With devtools enabled, `precept.core` accumulates an event log for each session operation. When `fire-rules` completes,
a batch of events are sent to the server along with the resultant state and the diff relative to the previous state. Calls to `fire-rules` (also referred to 
as "states") are id'd and numbered, along with each event within it. This allows us to refer to the first session event as state 0, event 0. Event 
numbers are zeroed out for each state and counts start at 0, so the 5th event of the 2nd fire rules call is referred to as `state 1, event 4`.

Each session operation is observable and information rich thanks to the Clara team's tremendous forethought and design decisions in this space. As a result, the event data contains:

- The type of operation (`insert-logical!`, unconditional insert from within a rule, unconditional insert from outside a rule, retract from outside a rule, retract within a rule)
- The rule that was involved (if any)
- The rule's conditions and consequences
- The facts that matched the rule's conditions
- The values bound to unification variables during the rule's execution (e.g. `?e` was `123`)
- The facts that were inserted or retracted as a conseqence
- Whether a logically-inserted fact was removed because a rule's condition no longer obtained (i.e., how and why truth maintenance took place)

Like most debugging tools, this allows us to show what happened during our program's execution, but we're also able to go a step further and explain why.


## Features

### Diff view
Shows facts that were added and removed for the selected state. Clicking on a fact in this view will create a tracker for it and show an explanation of its history over time.

The eye icons in the upper right of each fact indicates whether it's being viewed in the explanation sidebar. The icon will be orange if you are viewing an explanation 
that includes the exact instance of that fact, or black if you are viewing a different fact instance with the same eid and attribute.

### State view
View of all facts in the session for the selected state.
![image](https://user-images.githubusercontent.com/9045165/40588242-54591f6c-618f-11e8-806a-1edc9f8474ee.png)


### Event log
View of each event in the order it was recorded.

The icons in the left column show whether the consequence an event was an unconditional insertion, logical insertion, unconditional retraction, or a retraction caused by 
a rule's conditions ceasing to obtain.


### Fact tracking
Shows an explanation for a fact by its entity id and attribute. Initiated by clicking on a fact in the diff view. Shows an explanation for 
the requested event and every other event the fact was involved in. 

The list of the fact's occurrences and corresponding explanations are updated whenever new events take place in the inspected session.

Trackers are focalized to the `e-a` identity of a fact but can consist of one or more "views" on that fact at different points in time 

Additional views are automatically generated when the `[e a]` of the fact you want to inspect is already being tracked.

### Rule tracking
Lists all rules in the session, showing history for them if there is any.
Shows each event the rule participated in and an explanation for each. 
Updates whenever new events take place in the inspected session.

### Explanations
Shows why an event occurred. Varies according to the type of event.

Rule explanations:

![image](https://user-images.githubusercontent.com/9045165/40580581-12ef3316-60f6-11e8-8e6c-17fceb3464e2.png)



Shows the rule name, its conditions, the facts that matched those conditions, and the facts that were inserted or removed as a consequence. 
If the rule has variable bindings, shows the values that were bound to them at the time the rule fired. Pattern matches are color coded
within rule conditions and the corresponding parts of facts that matched them.



Schema enforcement explanations: 
![image](https://user-images.githubusercontent.com/9045165/40580381-d39803ea-60f1-11e8-8f67-cbfff3044c98.png)

Displays for events where a fact was removed from the session in order to comply with a user-defined schema. 
Shows the schema rule enforced, the inserted fact that triggered it, and the existing fact that was removed.

Action explanations: 
![image](https://user-images.githubusercontent.com/9045165/40580400-3703be56-60f2-11e8-89ca-7e5fe5bd78db.png)
Shows the facts inserted. Because actions effectively stipulate the existence of a fact, no further explanation is generated.



### Subscriptions
Lists each registered subscription and shows its previous and current values relative to the currently tracked state/fire-rules number.

### Actions
Shows facts that were inserted at the start of the selected state via `precept.core/then` that initiated all other events within the current state/`fire-rules`. 
