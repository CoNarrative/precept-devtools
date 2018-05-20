# precept-devtools
**alpha preview**

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
`start!` accepts devtools options that allow the application to send socket messages 
to the devtools server at its default location (`localhost:3232`).

As of this writing, the easiest way to see what the devtools are capable of is probably to 
run the latest todomvc or fullstack example in from the Precept repository:

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

Precept creates an event log with information about each consequence. When all rules are done firing,
the batch of events are sent to the server along with a list of facts that were inserted and retracted and the resultant state. States/rule firings are assigned a number, along with 
each event within them.

Owing to the Clara team's tremendous forethought and design decisions, each rule engine operation is observable and information rich. 
The event data shows:

- The operation that took place  (`insert-logical!`, unconditional insert from within a rule, unconditional insert from outside a rule, retract from outside a rule, retract within a rule)
- The rule that was involved (if any)
- The rule's conditions
- The fact or facts that matched those conditions
- The values bound to unification variables during the rule's execution (e.g. `?e` was `123`)
- The facts that were inserted or retracted
- That a logically-inserted fact was removed because a rule's condition no longer obtained (i.e., whether and why truth maintenance took place)

Like most debugging tools, this allows us to show what happened during our program's execution, but we can go a step further and also show why.


## Features

### Diff view
Shows facts that were added and removed for the selected state. Clicking on a fact in this view will create a tracker for it and show an explanation of its history over time.

The "eye" icons in the upper right of each fact indicates whether it's being viewed in the explanation sidebar. The icon will be orange if you are viewing an explanation 
that includes the exact instance of that fact, or black if you are viewing a different fact instance with the same eid and attribute.

### State view
View of all facts in the session for the selected state.

### Event log
View of each event in the order it was recorded.

The icons in the left column show whether the consequence an event was an unconditional insertion, logical insertion, unconditional retraction, or a retraction caused by 
a rule's conditions ceasing to obtain.


### Fact tracking
View of a fact by its entity id and attribute over the history of the session. A "tracker" can consist of one or more "viewers" to allow side-by-side comparison of a fact's value 
at multiple points in time.

### Rule tracking
Lists all rules in the session, showing history for them if there is any.

### Explanations
Shows why an event occurred. Varies according to the type of event.

Action explanations: Shows the facts inserted.

Schema enforcement explanations: Shows an upsert with the new fact that triggered it, the one that was removed, and the user-defined schema rule that was enforced.

Rule explanations: Shows the rule name, its conditions, the facts that matched those conditions, and the facts that were inserted or removed as a consequence. 
If the rule has variable bindings, shows the values bound to them at the time the rule fired.

### Subscriptions
Lists each registered subscription and shows its previous and current values relative to the selected state.

### Actions
Shows facts that were inserted for the first event of the selected state.
