//! 候选曝光的去重、输入片段边界与隐私保护。
use super::*;

#[test]
fn redraws_do_not_count_twice_but_new_commits_do() {
    let mut engine = engine().with_translator(Box::new(FixedTranslator));
    for _ in 0..2 {
        engine.set_input("kaifa");
        let mut query = engine.query().unwrap();
        engine.annotate(&mut query.candidates);
        for _ in 0..5 {
            engine.note_displayed(query.candidates.items.iter());
        }
        let chosen = query
            .candidates
            .items
            .iter()
            .find(|candidate| candidate.text == "开发")
            .unwrap();
        engine.commit(chosen);
    }
    let encounters = engine.take_encountered_terms();
    assert_eq!(
        encounters
            .iter()
            .filter(|encounter| encounter.term.word == "develop")
            .count(),
        2
    );
    assert!(
        encounters
            .iter()
            .any(|encounter| encounter.term.meaning == "开发")
    );
    assert!(engine.take_encountered_terms().is_empty());
}

#[test]
fn private_input_never_produces_learning_encounters() {
    let mut engine = engine().with_translator(Box::new(FixedTranslator));
    engine.set_private(true);
    engine.set_input("kaifa");
    let mut query = engine.query().unwrap();
    engine.annotate(&mut query.candidates);
    engine.note_displayed(query.candidates.items.iter());
    engine.commit(&query.candidates.items[0]);
    assert!(engine.take_encountered_terms().is_empty());
}
